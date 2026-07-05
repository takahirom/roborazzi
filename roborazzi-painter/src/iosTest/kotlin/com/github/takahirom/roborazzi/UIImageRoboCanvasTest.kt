package com.github.takahirom.roborazzi

import com.dropbox.differ.SimpleImageComparator
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSTemporaryDirectory
import kotlin.math.roundToInt
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
    // background in the TOP MARGIN above the first section (its box bottom edge
    // is anchored at y = margin, matching the JVM). Assert at least one dark,
    // mostly-opaque pixel exists in the margin band above the section (grid lines
    // are lighter gray), and none of the label glyph pixels sit at or below the
    // section's top edge.
    var labelPixelFound = false
    for (y in 0 until margin) {
      for (x in margin until minOf(margin + gw, compare.width)) {
        val p = compare.getPixel(x, y)
        if (p.a > 0.5f && p.r < 0.3f && p.g < 0.3f && p.b < 0.3f) {
          labelPixelFound = true
        }
      }
    }
    assertTrue(labelPixelFound, "grid comparison should render dark label glyph pixels in the top margin")

    golden.release()
    newCanvas.release()
    compare.release()
  }

  @Test
  fun gridLabelsSitAboveSectionsNotOverThem() {
    // Regression: the labels ("Reference"/"Diff"/"New") must sit in the top
    // margin, with their box bottom edge anchored at y = margin (JVM parity),
    // and must NOT paint over the section content below. Grid tiers are disabled
    // so the only non-golden pixels come from labels.
    fun solid(w: Int, h: Int, r: Int, g: Int, b: Int): ByteArray {
      val bytes = ByteArray(w * h * 4)
      for (i in 0 until w * h) {
        bytes[i * 4] = r.toByte()
        bytes[i * 4 + 1] = g.toByte()
        bytes[i * 4 + 2] = b.toByte()
        bytes[i * 4 + 3] = 255.toByte()
      }
      return bytes
    }
    val gw = 160
    val gh = 120
    val goldenColor = Triple(10, 20, 30)
    val golden = UIImageRoboCanvas.fromUnpremultipliedRgbaBytes(
      gw, gh, solid(gw, gh, goldenColor.first, goldenColor.second, goldenColor.third)
    )
    val newCanvas = UIImageRoboCanvas.fromUnpremultipliedRgbaBytes(
      gw, gh, solid(gw, gh, goldenColor.first, goldenColor.second, goldenColor.third)
    )

    val oneDpPx = 2f
    val margin = (16 * oneDpPx).toInt()
    val compare = UIImageRoboCanvas.generateCompareCanvas(
      goldenCanvas = golden,
      newCanvas = newCanvas,
      useGrid = true,
      oneDpPx = oneDpPx,
      bigLineSpaceDp = null,
      smallLineSpaceDp = null,
      hasLabel = true,
    )

    // 1) Label pixels must exist ABOVE the section top edge (y < margin), under
    //    the "Reference" label x-range. With grid disabled the margin band is
    //    otherwise fully transparent, so any non-transparent pixel here is label.
    var labelAboveMargin = false
    for (y in 0 until margin) {
      for (x in margin until minOf(margin + gw, compare.width)) {
        if (compare.getPixel(x, y).a > 0f) {
          labelAboveMargin = true
        }
      }
    }
    assertTrue(labelAboveMargin, "labels must be painted in the top margin above the sections")

    // 2) The top-left of the reference section (its very first content row, at
    //    y = margin, under the label x-range) must retain the golden solid color
    //    and NOT be blended with the label background. Before the fix the label
    //    was drawn downward from y = margin, covering exactly this pixel.
    val goldenPixel = com.dropbox.differ.Color(
      goldenColor.first, goldenColor.second, goldenColor.third, 255
    )
    assertEquals(
      goldenPixel,
      compare.getPixel(margin + 2, margin),
      "the reference section's top edge must keep the golden color, not the label background",
    )

    golden.release()
    newCanvas.release()
    compare.release()
  }

  @Test
  fun gridSectionsCoverGridLinesWithExactSourceColors() {
    // JVM parity (draw order): the grid lines and labels are drawn FIRST and the
    // three sections are composited ON TOP, so grid lines never cross the section
    // content. Inside a section's content area a pixel must equal its source
    // canvas color EXACTLY (no grid-line tint), even where a grid line falls,
    // while the margins still show the grid lines.
    fun solid(w: Int, h: Int, r: Int, g: Int, b: Int): ByteArray {
      val bytes = ByteArray(w * h * 4)
      for (i in 0 until w * h) {
        bytes[i * 4] = r.toByte()
        bytes[i * 4 + 1] = g.toByte()
        bytes[i * 4 + 2] = b.toByte()
        bytes[i * 4 + 3] = 255.toByte()
      }
      return bytes
    }
    val gw = 160
    val gh = 120
    val goldenColor = Triple(10, 20, 30)
    val newColor = Triple(200, 150, 100)
    val golden = UIImageRoboCanvas.fromUnpremultipliedRgbaBytes(
      gw, gh, solid(gw, gh, goldenColor.first, goldenColor.second, goldenColor.third)
    )
    val newCanvas = UIImageRoboCanvas.fromUnpremultipliedRgbaBytes(
      gw, gh, solid(gw, gh, newColor.first, newColor.second, newColor.third)
    )

    val oneDpPx = 2f
    val margin = (16 * oneDpPx).toInt() // 32
    val compare = UIImageRoboCanvas.generateCompareCanvas(
      goldenCanvas = golden,
      newCanvas = newCanvas,
      useGrid = true,
      oneDpPx = oneDpPx,
    )

    // (40, 40) sits inside the reference section content (below the top label
    // band) and lands exactly on grid lines (both x and y are multiples of the
    // 4dp*2 = 8px small-grid step). It must still be the pure golden color.
    val goldenPixel = com.dropbox.differ.Color(
      goldenColor.first, goldenColor.second, goldenColor.third, 255
    )
    assertEquals(
      goldenPixel,
      compare.getPixel(margin + 8, margin + 8),
      "reference section content on a grid line must keep the exact golden color",
    )

    // A point inside the "new" section (third section) on a grid line must be the
    // pure new color.
    val newPixel = com.dropbox.differ.Color(
      newColor.first, newColor.second, newColor.third, 255
    )
    assertEquals(
      newPixel,
      compare.getPixel(margin + gw * 2 + 8, margin + 8),
      "new section content on a grid line must keep the exact new color",
    )

    // Grid lines ARE present in the margin: the top-left corner (x=0, y=0) is in
    // the margin and lies on both grid axes, so it must be a non-transparent,
    // gray-ish grid pixel (not either section color).
    val corner = compare.getPixel(0, 0)
    assertTrue(corner.a > 0f, "grid line must be painted in the margin corner")
    assertTrue(corner != goldenPixel && corner != newPixel, "margin grid pixel must not be a section color")

    golden.release()
    newCanvas.release()
    compare.release()
  }

  @Test
  fun gridTierSpacingNullSkipsThatTier() {
    val golden = UIImageRoboCanvas.fromUnpremultipliedRgbaBytes(width, height, buildOpaqueBytes())
    val newCanvas = UIImageRoboCanvas.fromUnpremultipliedRgbaBytes(width, height, buildOpaqueBytes())
    val oneDpPx = 2f

    // With the default tiers enabled, the top-left corner is in the margin and
    // is painted by a grid line (both x=0 and y=0 lines).
    val withGrid = UIImageRoboCanvas.generateCompareCanvas(
      goldenCanvas = golden,
      newCanvas = newCanvas,
      useGrid = true,
      oneDpPx = oneDpPx,
      hasLabel = false,
    )
    assertTrue(withGrid.getPixel(0, 0).a > 0f, "default grid tiers should paint the corner grid line")

    // With both tiers disabled (null), no grid line is drawn, so the margin
    // corner stays transparent. A null spacing must not fall back to a default.
    val noGrid = UIImageRoboCanvas.generateCompareCanvas(
      goldenCanvas = golden,
      newCanvas = newCanvas,
      useGrid = true,
      oneDpPx = oneDpPx,
      bigLineSpaceDp = null,
      smallLineSpaceDp = null,
      hasLabel = false,
    )
    assertEquals(0f, noGrid.getPixel(0, 0).a, "disabling both grid tiers should leave the margin transparent")

    golden.release()
    newCanvas.release()
    withGrid.release()
    noGrid.release()
  }

  @Test
  fun compareCanvasScalesActualByResizeScale() {
    // The golden is saved at resizeScale, but the captured actual is full size.
    // generateCompareCanvas must scale the actual by newCanvasResize so the
    // sections line up, otherwise the compare image is 3x the full width and the
    // diff is misaligned.
    fun solid(w: Int, h: Int): ByteArray {
      val bytes = ByteArray(w * h * 4)
      for (i in 0 until w * h) {
        bytes[i * 4] = 100.toByte()
        bytes[i * 4 + 1] = 110.toByte()
        bytes[i * 4 + 2] = 120.toByte()
        bytes[i * 4 + 3] = 255.toByte()
      }
      return bytes
    }
    val gw = 80
    val gh = 60
    val golden = UIImageRoboCanvas.fromUnpremultipliedRgbaBytes(gw, gh, solid(gw, gh))
    // Full-size actual (2x) of the same solid color; recorded with resizeScale=0.5.
    val actualFull = UIImageRoboCanvas.fromUnpremultipliedRgbaBytes(gw * 2, gh * 2, solid(gw * 2, gh * 2))

    val compare = UIImageRoboCanvas.generateCompareCanvas(
      goldenCanvas = golden,
      newCanvas = actualFull,
      newCanvasResize = 0.5,
    )
    // Scaled actual is gw x gh, matching the golden, so the simple layout is
    // three gw-wide sections. Without scaling this would be 3 * (gw*2).
    assertEquals(gw * 3, compare.width, "actual should be scaled to match the golden section width")
    assertEquals(gh, compare.height)
    // Same solid color on both sides -> no diff highlight in the diff section.
    val red = com.dropbox.differ.Color(255, 0, 0, 255)
    assertTrue(
      compare.getPixel(gw + gw / 2, gh / 2) != red,
      "an unchanged (post-scale) pixel must not be marked red",
    )

    golden.release()
    actualFull.release()
    compare.release()
  }

  private fun channelDeviation(expected: Int, actual01: Float): Int {
    val actual = (actual01 * 255f).roundToInt()
    return kotlin.math.abs(expected - actual)
  }

  /**
   * Builds a 256x1 buffer where pixel x is the straight color (x, x, x, [alpha])
   * and returns the max per-channel deviation after each round-trip path:
   *   Pair(first = buffer -> canvas -> getPixel,
   *        second = buffer -> save(PNG) -> fromFile -> getPixel)
   */
  private fun roundTripDeviations(alpha: Int): Pair<Int, Int> {
    val w = 256
    val h = 1
    val bytes = ByteArray(w * h * 4)
    for (x in 0 until w) {
      val base = x * 4
      bytes[base] = x.toByte()
      bytes[base + 1] = x.toByte()
      bytes[base + 2] = x.toByte()
      bytes[base + 3] = alpha.toByte()
    }
    val canvas = UIImageRoboCanvas.fromUnpremultipliedRgbaBytes(w, h, bytes)
    var devA = 0
    for (x in 0 until w) {
      val p = canvas.getPixel(x, 0)
      devA = maxOf(devA, channelDeviation(x, p.r), channelDeviation(x, p.g), channelDeviation(x, p.b))
    }
    val path = tempPath("translucent-$alpha-${getTimeSuffix()}.png")
    canvas.save(path, 1.0, emptyMap(), pngFormat())
    val reloaded = UIImageRoboCanvas.fromFile(path)
    assertTrue(reloaded != null, "reloaded translucent canvas should not be null")
    var devB = 0
    for (x in 0 until w) {
      val p = reloaded.getPixel(x, 0)
      devB = maxOf(devB, channelDeviation(x, p.r), channelDeviation(x, p.g), channelDeviation(x, p.b))
    }
    canvas.release()
    reloaded.release()
    return devA to devB
  }

  private val characterizationAlphas = intArrayOf(1, 2, 8, 32, 64, 127, 128, 254)

  /**
   * Characterizes (and pins as a regression net) the premultiplied-alpha
   * precision loss. Because [UIImageRoboCanvas] must store premultiplied pixels
   * (a CGBitmapContext constraint) and un-premultiplies on read, translucent
   * channels are quantized by roughly 255/alpha. The observed max per-channel
   * deviation for the sweep below is:
   *
   *   alpha:      1    2    8   32   64  127  128  254
   *   maxDev:   127   64   16    4    2    1    1    1
   *
   * The PNG file round-trip loses exactly the same amount as the in-memory
   * canvas (no extra loss from encode/decode), which the assertions also pin.
   */
  @Test
  fun translucentPixelPrecisionIsBoundedAndPathIndependent() {
    val expectedMaxDeviation = mapOf(
      1 to 127, 2 to 64, 8 to 16, 32 to 4, 64 to 2, 127 to 1, 128 to 1, 254 to 1,
    )
    for (a in characterizationAlphas) {
      val (devA, devB) = roundTripDeviations(a)
      val bound = expectedMaxDeviation.getValue(a)
      assertTrue(devA <= bound, "alpha=$a canvas round-trip deviation $devA exceeds observed bound $bound")
      assertTrue(devB <= bound, "alpha=$a PNG round-trip deviation $devB exceeds observed bound $bound")
      assertEquals(devA, devB, "alpha=$a: PNG round-trip must lose the same as the in-memory canvas")
    }
  }

  /**
   * The precision loss is deterministic, so two canvases produced from the same
   * translucent buffer compare as identical. This is why real comparisons of
   * identically-produced images do not flake despite the loss.
   */
  @Test
  fun identicallyProducedTranslucentCanvasesCompareAsIdentical() {
    val w = 256
    val h = 1
    val bytes = ByteArray(w * h * 4)
    for (x in 0 until w) {
      val base = x * 4
      bytes[base] = x.toByte()
      bytes[base + 1] = (255 - x).toByte()
      bytes[base + 2] = ((x * 3) and 0xFF).toByte()
      bytes[base + 3] = 2.toByte() // alpha=2: worst-case quantization
    }
    val a = UIImageRoboCanvas.fromUnpremultipliedRgbaBytes(w, h, bytes)
    val b = UIImageRoboCanvas.fromUnpremultipliedRgbaBytes(w, h, bytes.copyOf())
    val result = a.differ(b, resizeScale = 1.0, imageComparator = SimpleImageComparator())
    assertEquals(
      0,
      result.pixelDifferences,
      "identically-produced translucent canvases must compare as identical despite premultiplication loss",
    )
    assertEquals(w * h, result.pixelCount)
    a.release()
    b.release()
  }

  // PNG-only on iOS; ImageIoFormat() is not implemented, so provide a stub that
  // save() ignores.
  private fun pngFormat(): ImageIoFormat = object : ImageIoFormat {}

  private fun getTimeSuffix(): Long = platform.CoreFoundation.CFAbsoluteTimeGetCurrent().toLong()
}
