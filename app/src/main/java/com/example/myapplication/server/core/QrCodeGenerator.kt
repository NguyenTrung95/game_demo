package com.example.myapplication.server.core

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayOutputStream

/**
 * Port of backend/core/qr.js. ZXing's javase MatrixToImageWriter needs java.awt.image.BufferedImage,
 * which doesn't exist on Android, so the BitMatrix -> Bitmap conversion is done by hand here.
 */
object QrCodeGenerator {

    private const val SIZE = 320
    private const val MARGIN = 1

    /** Returns a "data:image/png;base64,..." string, matching QRCode.toDataURL()'s output shape. */
    fun generateQrDataUrl(text: String): String {
        val bitmap = generateQrBitmap(text)
        val bytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        return "data:image/png;base64,$base64"
    }

    private fun generateQrBitmap(text: String): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to MARGIN
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, SIZE, SIZE, hints)
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
