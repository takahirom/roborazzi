package com.github.takahirom.roborazzi

import com.dropbox.differ.SimpleImageComparator
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The comparison of identical images is short circuited, so it has to agree with what
 * [SimpleImageComparator] would have reported.
 */
class IdenticalComparisonResultTest {

  @Test
  fun reportsNoDifferences_whenImagesAreIdentical() {
    val left = image { x, y -> pixel(x, y) }
    val right = image { x, y -> pixel(x, y) }

    val result = assertNotNull(identicalComparisonResult(left, right))

    assertEquals(0, result.pixelDifferences)
    assertEquals(WIDTH * HEIGHT, result.pixelCount)
    assertEquals(WIDTH, result.width)
    assertEquals(HEIGHT, result.height)
  }

  @Test
  fun agreesWithTheComparator_whenImagesAreIdentical() {
    val left = image { x, y -> pixel(x, y) }
    val right = image { x, y -> pixel(x, y) }

    val shortCircuited = assertNotNull(identicalComparisonResult(left, right))
    val compared = SimpleImageComparator()
      .compare(DifferBufferedImage(left), DifferBufferedImage(right))

    assertEquals(compared.pixelDifferences, shortCircuited.pixelDifferences)
    assertEquals(compared.pixelCount, shortCircuited.pixelCount)
    assertEquals(compared.width, shortCircuited.width)
    assertEquals(compared.height, shortCircuited.height)
  }

  @Test
  fun defersToTheComparator_whenASinglePixelDiffers() {
    val left = image { x, y -> pixel(x, y) }
    val right = image { x, y -> pixel(x, y) }
    right.setRGB(3, 4, 0xFFFFFFFF.toInt())

    assertNull(identicalComparisonResult(left, right))
  }

  @Test
  fun defersToTheComparator_whenSizesDiffer() {
    val left = image { x, y -> pixel(x, y) }
    val right = BufferedImage(WIDTH + 1, HEIGHT, BufferedImage.TYPE_INT_ARGB)

    assertNull(identicalComparisonResult(left, right))
  }

  @Test
  fun defersToTheComparator_whenPixelsAreNotPackedInts() {
    // A byte backed raster cannot be compared as an int array.
    val left = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_3BYTE_BGR)
    val right = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_3BYTE_BGR)

    assertNull(left.packedIntPixelsOrNull())
    assertNull(identicalComparisonResult(left, right))
  }

  @Test
  fun exposesPixels_forPackedIntImages() {
    val image = image { x, y -> pixel(x, y) }
    val pixels = assertNotNull(image.packedIntPixelsOrNull())
    assertEquals(WIDTH * HEIGHT, pixels.data.size)
    assertEquals(image.getRGB(2, 3), pixels.data[pixels.rowStart(3) + 2])
  }

  @Test
  fun reportsNoDifferences_forCroppedSubImages() {
    // A cropped canvas is a sub image, whose rows are strided over the larger parent array.
    val left = image { x, y -> pixel(x, y) }.getSubimage(0, 0, WIDTH - 5, HEIGHT - 3)
    val right = image { x, y -> pixel(x, y) }.getSubimage(0, 0, WIDTH - 5, HEIGHT - 3)

    val pixels = assertNotNull(left.packedIntPixelsOrNull())
    assertEquals(left.getRGB(2, 3), pixels.data[pixels.rowStart(3) + 2])

    val result = assertNotNull(identicalComparisonResult(left, right))
    assertEquals(0, result.pixelDifferences)
    assertEquals((WIDTH - 5) * (HEIGHT - 3), result.pixelCount)
  }

  @Test
  fun defersToTheComparator_whenACroppedSubImageDiffers() {
    val left = image { x, y -> pixel(x, y) }.getSubimage(0, 0, WIDTH - 5, HEIGHT - 3)
    val rightParent = image { x, y -> pixel(x, y) }
    rightParent.setRGB(1, 2, 0xFF00FF00.toInt())
    val right = rightParent.getSubimage(0, 0, WIDTH - 5, HEIGHT - 3)

    assertNull(identicalComparisonResult(left, right))
  }

  @Test
  fun ignoresPixelsOutsideTheCrop() {
    // Differences in the parent beyond the cropped region must not be reported.
    val left = image { x, y -> pixel(x, y) }.getSubimage(0, 0, WIDTH - 5, HEIGHT - 3)
    val rightParent = image { x, y -> pixel(x, y) }
    rightParent.setRGB(WIDTH - 1, HEIGHT - 1, 0xFF123456.toInt())
    val right = rightParent.getSubimage(0, 0, WIDTH - 5, HEIGHT - 3)

    assertNotNull(identicalComparisonResult(left, right))
  }

  private fun image(color: (x: Int, y: Int) -> Int): BufferedImage {
    val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until HEIGHT) {
      for (x in 0 until WIDTH) {
        image.setRGB(x, y, color(x, y))
      }
    }
    return image
  }

  private fun pixel(x: Int, y: Int): Int =
    (0xFF shl 24) or ((x * 7 and 0xFF) shl 16) or ((y * 11 and 0xFF) shl 8) or ((x + y) and 0xFF)

  private companion object {
    const val WIDTH = 16
    const val HEIGHT = 12
  }
}
