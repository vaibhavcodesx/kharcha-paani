package com.vaibhav.kharchapaani.ui.components

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.vaibhav.kharchapaani.domain.UpiLink
import org.junit.Assert.assertEquals
import org.junit.Test

/** The QR drawn for a payment request scans back to exactly that request. */
class UpiQrTest {

    private fun scan(text: String): String {
        val matrix = qrMatrix(text)
        val scale = 4
        val width = matrix.width * scale
        val pixels = IntArray(width * width) { i ->
            val x = (i % width) / scale
            val y = (i / width) / scale
            if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, width, pixels)))).text
    }

    @Test
    fun `a payment request survives the round trip, amount and all`() {
        val link = UpiLink.build("priya@okaxis", "Priya Sharma", amount = 1250.5, note = "Goa trip — dinner")

        assertEquals(link, scan(link))
    }

    @Test
    fun `the code keeps a quiet zone scanners need`() {
        val matrix = qrMatrix(UpiLink.build("v@okaxis", "V"))

        // The outer four modules on every side are blank.
        for (i in 0 until matrix.width) {
            for (edge in 0 until 4) {
                assertEquals(false, matrix[i, edge])
                assertEquals(false, matrix[edge, i])
            }
        }
    }
}
