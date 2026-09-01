// QR 扫码工具（§3.2.7 —— ZXing 二维码解码）
// MVP 简化：直接 decode BufferedImage；后续可换 CameraX + ML Kit
package com.elder.android.util

import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.awt.image.BufferedImage

object QrScanner {
    fun decode(image: BufferedImage): String? = try {
        val source = BufferedImageLuminanceSource(image)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        MultiFormatReader().decode(bitmap).text
    } catch (_: NotFoundException) {
        null
    } catch (_: Exception) {
        null
    }
}
