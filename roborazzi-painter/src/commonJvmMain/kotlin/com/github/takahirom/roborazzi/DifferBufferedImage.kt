package com.github.takahirom.roborazzi

import com.dropbox.differ.Color
import com.dropbox.differ.Image
import java.awt.image.BufferedImage

internal class DifferBufferedImage(private val bufferedImage: BufferedImage) : Image {
  override val height: Int
    get() = bufferedImage.height
  override val width: Int
    get() = bufferedImage.width

  override fun getPixel(x: Int, y: Int): Color {
    if (width <= x || height <= y) {
      // Waiting for dropbox differs next release to support size difference
      return Color(0, 0, 0, 0)
    }
    val color = Color(bufferedImage.getRGB(x, y))
    if (color.a == 0F) {
      // I'm not sure why, but WebP images return r = 1, g = 0, b = 0, a = 0 for transparent pixels.
      return Color(0, 0, 0, 0)
    }
    return color
  }
}