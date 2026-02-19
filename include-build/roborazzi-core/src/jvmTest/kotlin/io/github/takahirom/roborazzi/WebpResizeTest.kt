package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.resizeImageToFitMaxDimension
import java.awt.image.BufferedImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WebpResizeTest {

  @Test
  fun returnsOriginalImageWhenWithinMaxDimension() {
    val originalImage = BufferedImage(100, 60, BufferedImage.TYPE_INT_ARGB)

    val result = resizeImageToFitMaxDimension(originalImage, maxDimension = 16383)

    assertSame(originalImage, result)
  }

  @Test
  fun resizesImageWhenWidthExceedsMaxDimension() {
    val originalImage = BufferedImage(20000, 10000, BufferedImage.TYPE_INT_ARGB)

    val result = resizeImageToFitMaxDimension(originalImage, maxDimension = 16383)

    assertEquals(16383, result.width)
    assertEquals(8191, result.height)
  }

  @Test
  fun resizesImageWhenHeightExceedsMaxDimension() {
    val originalImage = BufferedImage(8000, 32000, BufferedImage.TYPE_INT_ARGB)

    val result = resizeImageToFitMaxDimension(originalImage, maxDimension = 16383)

    assertEquals(4095, result.width)
    assertEquals(16383, result.height)
  }
}
