package com.fashionstudio.aipro.core.imaging

import android.graphics.Bitmap

/**
 * Pure-Kotlin ARGB_8888 pixel buffer abstraction so all Product DNA Lock math
 * (segmentation, compositing, SSIM, CIEDE2000, Sobel edge preservation, diff heatmaps)
 * executes identically on Android devices and fast JVM JUnit tests.
 */
class PixelBuffer(
    val width: Int,
    val height: Int,
    val pixels: IntArray
) {
    init {
        require(width > 0 && height > 0) { "Dimensions must be positive" }
        require(pixels.size == width * height) { "Pixel array size must equal width * height" }
    }

    fun getPixel(x: Int, y: Int): Int = pixels[y * width + x]
    fun setPixel(x: Int, y: Int, argb: Int) { pixels[y * width + x] = argb }

    fun copy(): PixelBuffer = PixelBuffer(width, height, pixels.copyOf())

    companion object {
        fun fromBitmap(bitmap: Bitmap): PixelBuffer {
            val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
            else bitmap.copy(Bitmap.Config.ARGB_8888, false)
            val arr = IntArray(argb.width * argb.height)
            argb.getPixels(arr, 0, argb.width, 0, 0, argb.width, argb.height)
            return PixelBuffer(argb.width, argb.height, arr)
        }
    }

    fun toBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmp
    }
}

/**
 * Single-channel 8-bit alpha mask (0 = unprotected/background, 255 = strictly protected interior).
 */
class AlphaMask(
    val width: Int,
    val height: Int,
    val data: ByteArray
) {
    init {
        require(data.size == width * height) { "Mask size must equal width * height" }
    }

    fun alphaAt(x: Int, y: Int): Int = data[y * width + x].toInt() and 0xFF
    fun setAlpha(x: Int, y: Int, value: Int) {
        data[y * width + x] = value.coerceIn(0, 255).toByte()
    }

    fun copy(): AlphaMask = AlphaMask(width, height, data.copyOf())

    fun countProtectedPixels(threshold: Int = 250): Int {
        var count = 0
        for (b in data) {
            if ((b.toInt() and 0xFF) >= threshold) count++
        }
        return count
    }

    fun toBitmapMask(): Bitmap {
        val argb = IntArray(width * height)
        for (i in data.indices) {
            val a = data[i].toInt() and 0xFF
            argb[i] = (a shl 24) or (a shl 16) or (a shl 8) or a
        }
        return PixelBuffer(width, height, argb).toBitmap()
    }
}
