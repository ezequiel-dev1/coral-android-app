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

    /** ESC p m t1 t2 - drawer pulse, 100 ms ON / 100 ms OFF (0x32 = 50 x 2 ms). */
    fun drawerPulse(pin: Int = 0): ByteArray =
        byteArrayOf(0x1B, 0x70, (if (pin == 1) 0x01 else 0x00).toByte(), 0x32, 0x32)
}
