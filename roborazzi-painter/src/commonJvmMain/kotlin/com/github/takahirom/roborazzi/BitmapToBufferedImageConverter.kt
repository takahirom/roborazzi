package com.github.takahirom.roborazzi

import android.graphics.Bitmap
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt

object BitmapToBufferedImageConverter {
    fun convert(bitmap: Bitmap): BufferedImage {
        val width = bitmap.width
        val height = bitmap.height
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        // Bitmap.getPixels() returns non-premultiplied ARGB ints, which is exactly
        // the pixel layout of TYPE_INT_ARGB, so we can write straight into the
        // image's backing int array. This avoids setRGB()'s per-pixel ColorModel
        // conversion and an extra BufferedImage copy via drawImage().
        val data = (image.raster.dataBuffer as DataBufferInt).data
        bitmap.getPixels(data, 0, width, 0, 0, width, height)
        return image
    }
}
