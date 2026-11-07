package com.github.takahirom.roborazzi

import java.awt.image.BufferedImage

/**
 * Returns [image] itself when it can serve as the storage of a filled canvas, or a new copy
 * otherwise. Takes ownership of [image]: callers must not use it after this call.
 * Subimages are always copied because their storage belongs to the parent image.
 * Reusing opaque ARGB storage preserves every channel. Transparent images still pass
 * through Java2D so premultiplication rounding and zero-alpha colors remain unchanged.
 */
internal fun reuseOrCreateBitmapCanvasImage(image: BufferedImage, bufferedImageType: Int): BufferedImage {
  if (bufferedImageType == BufferedImage.TYPE_INT_ARGB &&
    image.type == BufferedImage.TYPE_INT_ARGB &&
    image.raster.parent == null &&
    image.hasOnlyOpaquePixels()
  ) {
    return image
  }

  val canvas = BufferedImage(image.width, image.height, bufferedImageType)
  val graphics = canvas.createGraphics()
  try {
    graphics.drawImage(image, 0, 0, image.width, image.height, null)
  } finally {
    graphics.dispose()
  }
  return canvas
}

private fun BufferedImage.hasOnlyOpaquePixels(): Boolean {
  val pixels = packedIntPixelsOrNull() ?: return false
  for (y in 0 until height) {
    val rowStart = pixels.rowStart(y)
    for (index in rowStart until rowStart + width) {
      if (pixels.data[index] ushr 24 != 255) {
        return false
      }
    }
  }
  return true
}
