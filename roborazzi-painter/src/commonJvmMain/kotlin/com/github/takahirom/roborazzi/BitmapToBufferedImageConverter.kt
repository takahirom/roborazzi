package com.github.takahirom.roborazzi

import android.graphics.Bitmap
import java.awt.image.BufferedImage

object BitmapToBufferedImageConverter {
    fun convert(bitmap: Bitmap): BufferedImage {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.drawImage(bitmapToImage(pixels, width, height), 0, 0, null)
        g.dispose()
        return image
    }

    private fun bitmapToImage(pixels: IntArray, width: Int, height: Int): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, width, height, pixels, 0, width)
        return image
    }
}