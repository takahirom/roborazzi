package com.github.takahirom.roborazzi

import com.dropbox.differ.SimpleImageComparator
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSTemporaryDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalRoborazziApi::class, ExperimentalForeignApi::class)
class UIImageRoboCanvasTest {

  private val width = 4
  private val height = 3

  /**
   * Builds a straight (un-premultiplied) opaque RGBA buffer where every pixel
   * gets a deterministic, distinct color derived from its coordinates.
   */
  private fun buildOpaqueBytes(
    colorAt: (x: Int, y: Int) -> Triple<Int, Int, Int> = { x, y ->
      Triple((x * 40) and 0xFF, (y * 60) and 0xFF, ((x + y) * 20) and 0xFF)
    }
  ): ByteArray {
    val bytes = ByteArray(width * height * 4)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val base = (y * width + x) * 4
        val (r, g, b) = colorAt(x, y)
        bytes[base] = r.toByte()
        bytes[base + 1] = g.toByte()
        bytes[base + 2] = b.toByte()
        bytes[base + 3] = 255.toByte()
      }
    }
    return bytes
  }

  private fun tempPath(name: String): String {
    val dir = NSTemporaryDirectory()
    return if (dir.endsWith("/")) "$dir$name" else "$dir/$name"
  }

  @Test
  fun roundTripThroughFileIsPixelIdentical() {
    val bytes = buildOpaqueBytes()
    val canvas = UIImageRoboCanvas.fromUnpremultipliedRgbaBytes(width, height, bytes)
    val path = tempPath("roborazzi-painter-roundtrip-${getTimeSuffix()}.png")
    canvas.save(
      path = path,
      resizeScale = 1.0,
      contextData = emptyMap(),
      imageIoFormat = pngFormat(),
    )

    val reloaded = UIImageRoboCanvas.fromFile(path)
    assertTrue(reloaded != null, "reloaded canvas should not be null")
    assertEquals(width, reloaded.width)
    assertEquals(height, reloaded.height)

    for (y in 0 until height) {
      for (x in 0 until width) {
        val original = canvas.getPixel(x, y)
        val loaded = reloaded.getPixel(x, y)
        assertEquals(original, loaded, "pixel mismatch at ($x, $y)")
      }
    }
    canvas.release()
    reloaded.release()
  }

  @Test
  fun differReportsZeroDiffForIdenticalCanvases() {
    val bytes = buildOpaqueBytes()
    val a = UIImageRoboCanvas.fromUnpremultipliedRgbaBytes(width, height, bytes)
    val b = UIImageRoboCanvas.fromUnpremultipliedRgbaBytes(width, height, bytes.copyOf())
    val result = a.differ(b, resizeScale = 1.0, imageComparator = SimpleImageComparator())
    assertEquals(0, result.pixelDifferences, "identical canvases must have zero diff")
    assertEquals(width * height, result.pixelCount)
    a.release()
    b.release()
  }

  @Test
  fun differReportsChangedPixels() {
    val golden = UIImageRoboCanvas.fromUnpremultipliedRgbaBytes(width, height, buildOpaqueBytes())
    // Flip 3 specific pixels to a strongly different color.
    val changed = setOf(0 to 0, 2 to 1, 3 to 2)
    val newBytes = buildOpaqueBytes { x, y ->
      if ((x to y) in changed) Triple(255, 255, 255) else {
        Triple((x * 40) and 0xFF, (y * 60) and 0xFF, ((x + y) * 20) and 0xFF)
      }
    }
    val newCanvas = UIImageRoboCanvas.fromUnpremultipliedRgbaBytes(width, height, newBytes)
    val result = golden.differ(newCanvas, resizeScale = 1.0, imageComparator = SimpleImageComparator())
    assertEquals(changed.size, result.pixelDifferences, "should report exactly the changed pixels")
    golden.release()
    newCanvas.release()
  }

  @Test
  fun generateCompareCanvasHasTripleWidthAndMarksDiff() {
    val golden = UIImageRoboCanvas.fromUnpremultipliedRgbaBytes(width, height, buildOpaqueBytes())
    val changed = setOf(1 to 1)
    val newBytes = buildOpaqueBytes { x, y ->
      if ((x to y) in changed) Triple(255, 255, 255) else {
        Triple((x * 40) and 0xFF, (y * 60) and 0xFF, ((x + y) * 20) and 0xFF)
      }
    }
    val newCanvas = UIImageRoboCanvas.fromUnpremultipliedRgbaBytes(width, height, newBytes)

    val compare = UIImageRoboCanvas.generateCompareCanvas(golden, newCanvas)
    assertEquals(width * 3, compare.width, "comparison image should be 3x the section width")
    assertEquals(height, compare.height)

    val red = com.dropbox.differ.Color(255, 0, 0, 255)
    // Diff section starts at x = width.
    val diffPixel = compare.getPixel(width + 1, 1)
    assertEquals(red, diffPixel, "changed pixel should be marked red in the diff section")
    // An unchanged pixel in the diff section must not be red.
    val unchanged = compare.getPixel(width + 0, 0)
    assertTrue(unchanged != red, "unchanged pixel should not be red in the diff section")

    golden.release()
    newCanvas.release()
    compare.release()
  }

  @Test
  fun gridComparisonCanvasHasMarginsGridAndLabels() {
    // A realistically sized image so the top-left labels stay within their
    // section (a few-pixel image would let a label overflow across sections).
    val gw = 160
    val gh = 120
    fun buffer(mutate: (x: Int, y: Int) -> Triple<Int, Int, Int>): ByteArray {
      val bytes = ByteArray(gw * gh * 4)
      for (y in 0 until gh) {
        for (x in 0 until gw) {
          val base = (y * gw + x) * 4
          val (r, g, b) = mutate(x, y)
          bytes[base] = r.toByte()
          bytes[base + 1] = g.toByte()
          bytes[base + 2] = b.toByte()
          bytes[base + 3] = 255.toByte()
        }
      }
      return bytes
    }
    val base: (Int, Int) -> Triple<Int, Int, Int> = { _, _ -> Triple(10, 20, 30) }
    val golden = UIImageRoboCanvas.fromUnpremultipliedRgbaBytes(gw, gh, buffer(base))
    // Change a single pixel low in the image, away from the top label band.
    val changedAt = 100 to 100
    val newCanvas = UIImageRoboCanvas.fromUnpremultipliedRgbaBytes(gw, gh, buffer { x, y ->
      if ((x to y) == changedAt) Triple(255, 255, 255) else base(x, y)
    })

    val oneDpPx = 2f
    val margin = (16 * oneDpPx).toInt()
    val compare = UIImageRoboCanvas.generateCompareCanvas(
      goldenCanvas = golden,
      newCanvas = newCanvas,
      useGrid = true,
      oneDpPx = oneDpPx,
    )

    // Grid layout adds a 16dp margin on every side around the three sections.
    assertEquals(gw * 3 + margin * 2, compare.width, "grid width should include side margins")
    assertEquals(gh + margin * 2, compare.height, "grid height should include top/bottom margins")

    // The changed pixel maps into the diff section (offset margin + section
    // width) and is below the label band and off the grid lines, so it stays
    // pure red.
    val red = com.dropbox.differ.Color(255, 0, 0, 255)
    assertEquals(
      red,
      compare.getPixel(margin + gw + changedAt.first, margin + changedAt.second),
      "changed pixel should be red in the diff section",
    )

    // The "Reference" label renders black glyph pixels over its translucent
    // background near the top-left of the first section. Assert at least one
    // dark, mostly-opaque pixel exists there (grid lines are lighter gray).
    var labelPixelFound = false
    val bandBottom = minOf(margin + (12 * oneDpPx).toInt() + margin * 2, compare.height)
    for (y in margin until bandBottom) {
      for (x in margin until minOf(margin + gw, compare.width)) {
        val p = compare.getPixel(x, y)
        if (p.a > 0.5f && p.r < 0.3f && p.g < 0.3f && p.b < 0.3f) {
          labelPixelFound = true
        }
      }
    }
    assertTrue(labelPixelFound, "grid comparison should render dark label glyph pixels")

    golden.release()
    newCanvas.release()
    compare.release()
  }

  // PNG-only on iOS; ImageIoFormat() is not implemented, so provide a stub that
  // save() ignores.
  private fun pngFormat(): ImageIoFormat = object : ImageIoFormat {}

  private fun getTimeSuffix(): Long = platform.CoreFoundation.CFAbsoluteTimeGetCurrent().toLong()
}
