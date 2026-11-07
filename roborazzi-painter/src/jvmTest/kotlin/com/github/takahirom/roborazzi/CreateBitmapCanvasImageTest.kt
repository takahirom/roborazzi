package com.github.takahirom.roborazzi

import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/** Protects ownership and the existing Java2D alpha conversion of captured images. */
class CreateBitmapCanvasImageTest {
  @Test
  fun reusesOpaqueArgbStorageWithIdenticalPixels() {
    val image = image { x, y ->
      0xFF000000.toInt() or (x shl 16) or (y shl 8) or ((x * 103 + y * 41) and 255)
    }
    val expected = legacyCanvas(image, BufferedImage.TYPE_INT_ARGB)

    val actual = createBitmapCanvasImage(image, BufferedImage.TYPE_INT_ARGB)

    assertSame(image, actual)
    assertContentEquals(pixels(expected), pixels(actual))
  }

  @Test
  fun preservesSrcOverRoundingForEveryAlphaAndChannelValue() {
    val image = image { x, y ->
      (y shl 24) or (x shl 16) or ((255 - x) shl 8) or ((x * 103 + y * 41) and 255)
    }
    val before = pixels(image)
    val expected = legacyCanvas(image, BufferedImage.TYPE_INT_ARGB)

    val actual = createBitmapCanvasImage(image, BufferedImage.TYPE_INT_ARGB)

    assertNotSame(image, actual)
    assertContentEquals(pixels(expected), pixels(actual))
    assertContentEquals(before, pixels(image))
  }

  @Test
  fun checksTheLastPixelBeforeReusingStorage() {
    val image = image { _, _ -> 0xFF468ABE.toInt() }
    image.setRGB(image.width - 1, image.height - 1, 0x016EAAD2)
    val expected = legacyCanvas(image, BufferedImage.TYPE_INT_ARGB)

    val actual = createBitmapCanvasImage(image, BufferedImage.TYPE_INT_ARGB)

    assertNotSame(image, actual)
    assertContentEquals(pixels(expected), pixels(actual))
  }

  @Test
  fun preservesColorConversionForOtherCanvasFormats() {
    for (type in listOf(
      BufferedImage.TYPE_INT_ARGB_PRE,
      BufferedImage.TYPE_INT_RGB,
      BufferedImage.TYPE_USHORT_565_RGB,
    )) {
      val image = image { x, y ->
        0xFF000000.toInt() or (x shl 16) or (y shl 8) or ((x * 103 + y * 41) and 255)
      }
      val expected = legacyCanvas(image, type)

      val actual = createBitmapCanvasImage(image, type)

      assertNotSame(image, actual)
      assertEquals(type, actual.type)
      assertContentEquals(pixels(expected), pixels(actual), "type=$type")
    }
  }

  private fun image(pixel: (x: Int, y: Int) -> Int): BufferedImage {
    return BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB).apply {
      val data = (raster.dataBuffer as DataBufferInt).data
      for (y in 0 until height) {
        for (x in 0 until width) {
          data[y * width + x] = pixel(x, y)
        }
      }
    }
  }

  private fun pixels(image: BufferedImage): IntArray {
    return image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
  }

  private fun legacyCanvas(image: BufferedImage, type: Int): BufferedImage {
    val canvas = BufferedImage(image.width, image.height, type)
    val graphics = canvas.createGraphics()
    try {
      graphics.drawImage(image, 0, 0, image.width, image.height, null)
    } finally {
      graphics.dispose()
    }
    return canvas
  }
}
