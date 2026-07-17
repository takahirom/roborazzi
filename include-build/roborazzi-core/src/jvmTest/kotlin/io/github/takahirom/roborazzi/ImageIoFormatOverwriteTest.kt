package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.JvmImageIoFormat
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression test for https://github.com/takahirom/roborazzi/issues/881
 *
 * When a golden file is overwritten with a smaller image, the writer must
 * truncate the existing file. Otherwise trailing bytes from the previous
 * image remain and the file size no longer matches the size recorded in the
 * WebP header, which makes webp-imageio reject the file (ImageIO.read
 * returns null).
 */
class ImageIoFormatOverwriteTest {

  @Test
  fun webpGoldenFileCanBeLoadedAfterOverwritingWithSmallerImage() {
    val format = LosslessWebPImageIoFormat() as JvmImageIoFormat
    val file = File.createTempFile("golden", ".webp").apply { deleteOnExit() }

    format.awtImageWriter.write(file, emptyMap(), noiseImage(size = 256))
    val largeFileLength = file.length()

    format.awtImageWriter.write(file, emptyMap(), noiseImage(size = 32))
    assert(file.length() < largeFileLength) {
      "Overwritten file should be truncated: was $largeFileLength, now ${file.length()}"
    }

    val loaded = format.awtImageLoader.load(file)
    assertEquals(32, loaded.width)
    assertEquals(32, loaded.height)
  }

  @Test
  fun imageWithMetadataCanBeLoadedAfterOverwritingWithSmallerImage() {
    val format = JvmImageIoFormat()
    val contextData = mapOf<String, Any>("roborazzi_description" to "test")
    val file = File.createTempFile("golden", ".png").apply { deleteOnExit() }

    format.awtImageWriter.write(file, contextData, noiseImage(size = 256))

    format.awtImageWriter.write(file, contextData, noiseImage(size = 32))
    val freshFile = File.createTempFile("golden_fresh", ".png").apply { deleteOnExit() }
    format.awtImageWriter.write(freshFile, contextData, noiseImage(size = 32))
    assertEquals(
      freshFile.length(), file.length(),
      "Overwritten file should not contain trailing bytes of the previous image"
    )

    val loaded = format.awtImageLoader.load(file)
    assertEquals(32, loaded.width)
    assertEquals(32, loaded.height)
  }

  // Deterministic noise so that a larger image always produces a larger
  // compressed file than a smaller one.
  private fun noiseImage(size: Int): BufferedImage {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    var seed = 0x12345678
    for (y in 0 until size) {
      for (x in 0 until size) {
        seed = seed * 1664525 + 1013904223
        image.setRGB(x, y, seed or 0xFF000000.toInt())
      }
    }
    return image
  }
}
