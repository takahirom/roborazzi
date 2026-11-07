package com.github.takahirom.roborazzi

import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt

/**
 * Takes ownership of a newly converted Bitmap image for a filled canvas.
 * Reusing opaque ARGB storage preserves every channel. Transparent images still pass
 * through Java2D so premultiplication rounding and zero-alpha colors remain unchanged.
 * The conversion fallback flushes the consumed image after copying its pixels.
 */
internal fun createBitmapCanvasImage(image: BufferedImage, bufferedImageType: Int): BufferedImage {
  if (bufferedImageType == BufferedImage.TYPE_INT_ARGB && image.type == BufferedImage.TYPE_INT_ARGB) {
    val pixels = (image.raster.dataBuffer as DataBufferInt).data
    if (pixels.all { it ushr 24 == 255 }) {
      return image
    }
  }

  val canvas = BufferedImage(image.width, image.height, bufferedImageType)
  val graphics = canvas.createGraphics()
  try {
    graphics.drawImage(image, 0, 0, image.width, image.height, null)
  } finally {
    graphics.dispose()
    image.flush()
  }
  return canvas
}
