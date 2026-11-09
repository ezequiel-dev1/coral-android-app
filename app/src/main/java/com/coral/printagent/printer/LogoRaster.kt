package com.coral.printagent.printer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.coral.printagent.R
import java.io.ByteArrayOutputStream

/** Converts the bundled Coral logo into an ESC/POS raster image (GS v 0). */
object LogoRaster {
    /** Logo width in printer dots. The NE-511 prints 576 dots on 80 mm paper; 384 leaves margins. */
    const val WIDTH_DOTS = 384

    /** Anything darker than this (after compositing on white) prints black. Keeps the orange coral. */
    private const val THRESHOLD = 215

    fun coralLogo(context: Context): ByteArray {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val src = BitmapFactory.decodeResource(context.resources, R.drawable.coral_logo, opts)
            ?: error("Logo resource could not be decoded")
        val height = Math.round(src.height * WIDTH_DOTS / src.width.toFloat())
        val bmp = Bitmap.createScaledBitmap(src, WIDTH_DOTS, height, true)
        val bytesPerRow = WIDTH_DOTS / 8
        val data = ByteArray(bytesPerRow * height)
        for (y in 0 until height) {
            for (x in 0 until WIDTH_DOTS) {
                val p = bmp.getPixel(x, y)
                val a = Color.alpha(p) / 255f
                // Composite over white paper.
                val r = 255 - a * (255 - Color.red(p))
                val g = 255 - a * (255 - Color.green(p))
                val b = 255 - a * (255 - Color.blue(p))
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                if (lum < THRESHOLD) {
                    data[y * bytesPerRow + x / 8] =
                        (data[y * bytesPerRow + x / 8].toInt() or (0x80 shr (x % 8))).toByte()
                }
            }
        }
        val out = ByteArrayOutputStream()
        // GS v 0 m xL xH yL yH d1...dk  (m = 0: normal density)
        out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00))
        out.write(bytesPerRow and 0xFF)
        out.write((bytesPerRow shr 8) and 0xFF)
        out.write(height and 0xFF)
        out.write((height shr 8) and 0xFF)
        out.write(data)
        return out.toByteArray()
    }
}
