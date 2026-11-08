package com.coral.printagent.printer

object EscPosCommands {
    /** ESC @ - initialize printer. */
    val INITIALIZE = byteArrayOf(0x1B, 0x40)

    /** GS V 0 - full cut. */
    val CUT = byteArrayOf(0x1D, 0x56, 0x00)

    private const val LINE = "--------------------------------"
    private const val DOUBLE_LINE = "================================"

    private fun center(text: String, width: Int = 32): String =
        " ".repeat(((width - text.length) / 2).coerceAtLeast(0)) + text

    /** Sample receipt (32 columns). Set [includeCut] to false for troubleshooting. */
    fun testReceipt(includeCut: Boolean = true): ByteArray {
        val text = listOf(
            DOUBLE_LINE,
            center("CORAL REEF & BEEF"),
            DOUBLE_LINE,
            center("PRINTER TEST"),
            "Printer: Nextep NE-511",
            "Connection: Ethernet",
            "Status: Test command received",
            LINE,
            center("TEST SUCCESSFUL"),
            LINE,
        ).joinToString("\n") + "\n\n\n\n"
        val body = text.toByteArray(Charsets.US_ASCII)
        return if (includeCut) INITIALIZE + body + CUT else INITIALIZE + body
    }

    private val codePage: java.nio.charset.Charset =
        runCatching { java.nio.charset.Charset.forName("IBM437") }.getOrDefault(Charsets.US_ASCII)

    /** Builds ESC/POS bytes for an arbitrary [PrintJob]. */
    fun fromJob(job: com.coral.printagent.model.PrintJob, drawerPin: Int = 0): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(INITIALIZE)
        // ESC t 0 - select code page PC437 to match the encoding used below.
        out.write(byteArrayOf(0x1B, 0x74, 0x00))
        if (job.openDrawer) out.write(drawerPulse(drawerPin))
        for (line in job.lines) {
            val align = when (line.align) {
                com.coral.printagent.model.Align.LEFT -> 0
                com.coral.printagent.model.Align.CENTER -> 1
                com.coral.printagent.model.Align.RIGHT -> 2
            }
            val n = line.size - 1
            out.write(byteArrayOf(0x1B, 0x61, align.toByte()))
            out.write(byteArrayOf(0x1B, 0x45, if (line.bold) 1 else 0))
            out.write(byteArrayOf(0x1B, 0x2D, if (line.underline) 1 else 0))
            out.write(byteArrayOf(0x1D, 0x21, ((n shl 4) or n).toByte()))
            out.write(line.text.replace("\r", "").toByteArray(codePage))
            out.write(0x0A)
        }
        // Reset styles.
        out.write(byteArrayOf(0x1B, 0x61, 0x00, 0x1B, 0x45, 0x00, 0x1B, 0x2D, 0x00, 0x1D, 0x21, 0x00))
        repeat(job.feed) { out.write(0x0A) }
        if (job.cut) out.write(CUT)
        return out.toByteArray()
    }

    /** ESC p m t1 t2 - drawer pulse, 100 ms ON / 100 ms OFF (0x32 = 50 x 2 ms). */
    fun drawerPulse(pin: Int = 0): ByteArray =
        byteArrayOf(0x1B, 0x70, (if (pin == 1) 0x01 else 0x00).toByte(), 0x32, 0x32)
}
