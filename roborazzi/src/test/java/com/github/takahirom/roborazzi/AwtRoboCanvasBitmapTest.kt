package com.github.takahirom.roborazzi

import android.graphics.Bitmap
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Checks that a canvas created from a Bitmap matches drawing the Bitmap onto a filled canvas. */
@OptIn(InternalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AwtRoboCanvasBitmapTest {
  private val argbImageType = RoborazziOptions.PixelBitConfig.Argb8888.toBufferedImageType()

  @Test
  fun matchesDrawnCanvasForOpaqueBitmap() {
    assertMatchesDrawnCanvas(
      bitmap { x, y -> 0xFF000000.toInt() or (x shl 16) or (y shl 8) or ((x * 7 + y) and 255) }
    )
  }

  @Test
  fun matchesDrawnCanvasForTranslucentBitmap() {
    assertMatchesDrawnCanvas(
      bitmap { x, y -> ((x + y) and 255 shl 24) or (x shl 16) or (y shl 8) or 0x80 }
    )
  }

  @Test
  fun doesNotShareStorageWithBitmap() {
    val bitmap = bitmap { _, _ -> 0xFF468ABE.toInt() }
    val canvas = AwtRoboCanvas(bitmap, argbImageType)

    bitmap.setPixel(0, 0, 0xFF000000.toInt())

    assertEquals(0xFF468ABE.toInt(), canvas.getPixel(0, 0))
    canvas.release()
  }

  private fun assertMatchesDrawnCanvas(bitmap: Bitmap) {
    val expected = AwtRoboCanvas(
      width = bitmap.width,
      height = bitmap.height,
      filled = true,
      bufferedImageType = argbImageType
    ).apply {
      drawImage(Rect(0, 0, bitmap.width, bitmap.height), bitmap)
    }

    val actual = AwtRoboCanvas(bitmap, argbImageType)

    assertEquals(expected.width, actual.width)
    assertEquals(expected.height, actual.height)
    assertEquals(expected.croppedWidth, actual.croppedWidth)
    assertEquals(expected.croppedHeight, actual.croppedHeight)
    assertEquals(expected.emptyPoints, actual.emptyPoints)
    for (y in 0 until bitmap.height) {
      for (x in 0 until bitmap.width) {
        assertEquals("pixel ($x, $y)", expected.getPixel(x, y), actual.getPixel(x, y))
      }
    }
    expected.release()
    actual.release()
  }

  private fun bitmap(pixel: (x: Int, y: Int) -> Int): Bitmap {
    val width = 130
    val height = 90
    val pixels = IntArray(width * height) { index -> pixel(index % width, index / width) }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
      setPixels(pixels, 0, width, 0, 0, width, height)
    }
  }
}
