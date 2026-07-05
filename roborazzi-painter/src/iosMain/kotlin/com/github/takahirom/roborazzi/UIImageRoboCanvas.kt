@file:OptIn(ExperimentalForeignApi::class, InternalRoborazziApi::class)

package com.github.takahirom.roborazzi

import com.dropbox.differ.Color
import com.dropbox.differ.Image
import com.dropbox.differ.ImageComparator
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGBitmapContextGetBytesPerRow
import platform.CoreGraphics.CGBitmapContextGetData
import platform.CoreGraphics.CGColorSpaceCreateWithName
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGColorSpaceRef
import platform.CoreGraphics.CGContextClearRect
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRef
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGDataProviderCreateWithCFData
import platform.CoreGraphics.CGDataProviderRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageCreate
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.kCGBitmapByteOrder32Little
import platform.CoreGraphics.kCGColorSpaceSRGB
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.writeToFile
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsPopContext
import platform.UIKit.UIGraphicsPushContext
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.drawAtPoint
import platform.UIKit.sizeWithAttributes
import kotlin.math.roundToInt
import kotlinx.cinterop.useContents

/**
 * iOS implementation of [RoboCanvas] backed by a CoreGraphics bitmap context.
 *
 * Image format knowledge ported from `roborazzi-compose-ios`'s `RoborazziIos.kt`:
 * - The internal bitmap context uses sRGB with
 *   `kCGImageAlphaPremultipliedFirst or kCGBitmapByteOrder32Little`.
 *   `kCGImageAlphaFirst` (straight alpha) is NOT supported for
 *   `CGBitmapContextCreate` on iOS, so the context always stores
 *   premultiplied pixels. With alpha-first + byte-order-32-little the bytes are
 *   laid out in memory as B, G, R, A per pixel.
 * - Incoming straight (un-premultiplied) RGBA pixel buffers are turned into a
 *   source `CGImage` (`kCGImageAlphaLast`) and drawn into the context, letting
 *   CoreGraphics premultiply automatically.
 * - Golden images loaded from a PNG file must be re-drawn into a context of the
 *   canonical format above before they can be compared (the loaded `CGImage`
 *   may use a different color space / bitmap layout).
 * - When exposing pixels for comparison ([DifferCGImage]) the premultiplied
 *   bytes are un-premultiplied so the values match what the JVM
 *   `DifferBufferedImage` sees (BufferedImage.getRGB returns straight alpha).
 *
 * Precision note: because storage is premultiplied, translucent pixels lose
 * color precision proportional to `255 / alpha` (fully opaque pixels round-trip
 * losslessly; the loss grows as alpha drops — at most ~1 per channel at
 * alpha>=127, ~64 at alpha=2, and ~127 at alpha=1, the worst case). The loss
 * is deterministic, so comparisons of
 * identically-produced images are unaffected; only cross-source comparisons of
 * low-alpha content may need a small threshold. The JVM `AwtRoboCanvas` stores
 * straight ARGB and has no such loss. See UIImageRoboCanvasTest for the pinned
 * deviation envelope.
 */
@ExperimentalRoborazziApi
class UIImageRoboCanvas private constructor(
  override val width: Int,
  override val height: Int,
  internal val context: CGContextRef,
) : RoboCanvas {
  override val croppedWidth: Int get() = width
  override val croppedHeight: Int get() = height

  private var released = false

  /**
   * Returns the straight (un-premultiplied) RGBA value of the pixel at [x], [y].
   * The internal buffer stores premultiplied BGRA, so alpha is divided back out
   * to match the values produced on the JVM.
   */
  internal fun getPixel(x: Int, y: Int): Color {
    check(!released) { "UIImageRoboCanvas is already released" }
    val data = CGBitmapContextGetData(context)?.reinterpret<UByteVar>()
      ?: return Color(0, 0, 0, 0)
    val bytesPerRow = CGBitmapContextGetBytesPerRow(context).toInt()
    val base = y * bytesPerRow + x * 4
    // Memory layout is B, G, R, A (alpha-first + byte-order-32-little).
    val b = data[base].toInt() and 0xFF
    val g = data[base + 1].toInt() and 0xFF
    val r = data[base + 2].toInt() and 0xFF
    val a = data[base + 3].toInt() and 0xFF
    if (a == 0) return Color(0, 0, 0, 0)
    // Un-premultiply, rounding to the nearest straight value so translucent
    // pixels do not drift by one from what the JVM sees.
    val sr = ((r * 255 + a / 2) / a).coerceAtMost(255)
    val sg = ((g * 255 + a / 2) / a).coerceAtMost(255)
    val sb = ((b * 255 + a / 2) / a).coerceAtMost(255)
    return Color(sr, sg, sb, a)
  }

  private fun toCGImage(): CGImageRef? = CGBitmapContextCreateImage(context)

  override fun save(
    path: String,
    resizeScale: Double,
    contextData: Map<String, Any>,
    // contextData is currently ignored on iOS; only PNG output is supported so
    // imageIoFormat is not consulted either.
    imageIoFormat: ImageIoFormat,
  ) {
    check(!released) { "UIImageRoboCanvas is already released" }
    val outCanvas = if (resizeScale == 1.0) this else scaled(resizeScale)
    try {
      val cgImage = outCanvas.toCGImage() ?: run {
        roborazziReportLog("Failed to create CGImage for $path")
        return
      }
      try {
        val uiImage = UIImage.imageWithCGImage(cgImage)
        val parentPath = path.substringBeforeLast("/")
        if (parentPath.isNotEmpty() && parentPath != path) {
          NSFileManager.defaultManager.createDirectoryAtPath(
            parentPath,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
          )
        }
        val png = UIImagePNGRepresentation(uiImage)
        if (png == null) {
          roborazziReportLog("Failed to encode PNG for $path")
          return
        }
        if (png.writeToFile(path, true)) {
          roborazziReportLog("Image is saved $path")
        } else {
          roborazziReportLog("Failed to write image to $path")
        }
      } finally {
        CGImageRelease(cgImage)
      }
    } finally {
      if (outCanvas !== this) outCanvas.release()
    }
  }

  override fun differ(
    other: RoboCanvas,
    resizeScale: Double,
    imageComparator: ImageComparator
  ): ImageComparator.ComparisonResult {
    check(!released) { "UIImageRoboCanvas is already released" }
    other as UIImageRoboCanvas
    val left = if (resizeScale == 1.0) this else scaled(resizeScale)
    try {
      return imageComparator.compare(
        DifferCGImage(left),
        DifferCGImage(other)
      )
    } finally {
      if (left !== this) left.release()
    }
  }

  /**
   * Renders this canvas into a new canvas scaled by [scale]. Caller owns the
   * returned canvas and must [release] it.
   */
  private fun scaled(scale: Double): UIImageRoboCanvas {
    val scaledWidth = (width * scale).toInt().coerceAtLeast(1)
    val scaledHeight = (height * scale).toInt().coerceAtLeast(1)
    val scaledCanvas = create(scaledWidth, scaledHeight)
    val cgImage = toCGImage()
    if (cgImage != null) {
      CGContextDrawImage(
        scaledCanvas.context,
        CGRectMake(0.0, 0.0, scaledWidth.toDouble(), scaledHeight.toDouble()),
        cgImage
      )
      CGImageRelease(cgImage)
    }
    return scaledCanvas
  }

  override fun release() {
    if (released) return
    CGContextRelease(context)
    released = true
  }

  companion object {
    /** Creates an empty (fully transparent) canvas of the given size. */
    fun create(width: Int, height: Int): UIImageRoboCanvas {
      val context = createContext(width, height)
      CGContextClearRect(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
      return UIImageRoboCanvas(width, height, context)
    }

    /**
     * Creates a canvas from a straight (un-premultiplied) RGBA byte buffer.
     * [bytes] must contain `width * height * 4` bytes laid out as R, G, B, A
     * per pixel (top row first). CoreGraphics premultiplies the pixels when it
     * draws them into the internal premultiplied context.
     *
     * This signature is convenient for a later Compose PixelMap adapter, which
     * can flatten its pixels into a straight RGBA buffer.
     */
    fun fromUnpremultipliedRgbaBytes(
      width: Int,
      height: Int,
      bytes: ByteArray
    ): UIImageRoboCanvas {
      require(bytes.size >= width * height * 4) {
        "bytes must contain at least width * height * 4 elements"
      }
      val canvas = create(width, height)
      val sourceImage = createStraightRgbaImage(width, height, bytes)
      if (sourceImage == null) {
        // Returning the cleared canvas here would silently replace the
        // requested image with transparent pixels.
        canvas.release()
        error("Failed to create CGImage from the ${width}x$height pixel buffer")
      }
      CGContextDrawImage(
        canvas.context,
        CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()),
        sourceImage
      )
      CGImageRelease(sourceImage)
      return canvas
    }

    /**
     * Loads a PNG golden image from [path] and re-draws it into the canonical
     * premultiplied context so that it can be compared against a freshly
     * captured canvas. Returns null when the file does not exist or cannot be
     * decoded.
     */
    fun fromFile(path: String): UIImageRoboCanvas? {
      if (!NSFileManager.defaultManager.fileExistsAtPath(path)) {
        return null
      }
      val uiImage = UIImage(contentsOfFile = path)
      val cgImage = uiImage.CGImage
      if (cgImage == null) {
        roborazziReportLog("can't read CGImage from $path")
        return null
      }
      val width = CGImageGetWidth(cgImage).toInt()
      val height = CGImageGetHeight(cgImage).toInt()
      val canvas = create(width, height)
      CGContextDrawImage(
        canvas.context,
        CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()),
        cgImage
      )
      return canvas
    }

    private fun createContext(width: Int, height: Int): CGContextRef {
      require(width > 0 && height > 0) { "Invalid canvas size: ${width}x$height" }
      require(width.toLong() * height * 4 <= Int.MAX_VALUE) {
        "Canvas size ${width}x$height exceeds the supported pixel buffer size"
      }
      val colorSpace = CGColorSpaceCreateWithName(kCGColorSpaceSRGB)
      // kCGImageAlphaFirst is not supported for CGBitmapContextCreate on iOS,
      // so we always store premultiplied pixels.
      val bitmapInfo =
        CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst.value or kCGBitmapByteOrder32Little
      val context = CGBitmapContextCreate(
        data = null,
        width = width.convert(),
        height = height.convert(),
        bitsPerComponent = 8u,
        bytesPerRow = (width * 4).convert(),
        space = colorSpace,
        bitmapInfo = bitmapInfo
      )
      // The context retains its own reference to the color space.
      CGColorSpaceRelease(colorSpace)
      return requireNotNull(context) { "Failed to create CGBitmapContext ${width}x$height" }
    }

    private fun createStraightRgbaImage(
      width: Int,
      height: Int,
      bytes: ByteArray
    ): CGImageRef? {
      val size = width * height * 4
      return memScoped {
        val buffer = allocArray<ByteVar>(size)
        for (i in 0 until size) {
          buffer[i] = bytes[i]
        }
        val data = CFDataCreate(null, buffer.reinterpret(), size.convert()) ?: return@memScoped null
        val provider = CGDataProviderCreateWithCFData(data)
        CFRelease(data)
        if (provider == null) return@memScoped null
        val colorSpace = CGColorSpaceCreateWithName(kCGColorSpaceSRGB)
        // Straight RGBA in memory (R, G, B, A) == alpha-last, default byte order.
        val image = CGImageCreate(
          width = width.convert(),
          height = height.convert(),
          bitsPerComponent = 8u,
          bitsPerPixel = 32u,
          bytesPerRow = (width * 4).convert(),
          space = colorSpace,
          bitmapInfo = CGImageAlphaInfo.kCGImageAlphaLast.value,
          provider = provider,
          decode = null,
          shouldInterpolate = false,
          intent = platform.CoreGraphics.CGColorRenderingIntent.kCGRenderingIntentDefault
        )
        CGColorSpaceRelease(colorSpace)
        CGDataProviderRelease(provider)
        image
      }
    }

    /**
     * Produces a comparison canvas laid out as `reference | diff | new`.
     * Differing pixels in the middle (diff) section are highlighted in red.
     *
     * When [useGrid] is true and a valid [oneDpPx] density is available this
     * mirrors the JVM `ComparisonStyle.Grid` output: the three sections are
     * inset by a 16dp margin, overlaid with 4dp / 16dp grid lines and, when
     * [hasLabel] is set, "Reference" / "Diff" / "New" text labels. Otherwise it
     * renders the JVM "Simple" style (three sections, no margins/grid/labels).
     * This matches AwtRoboCanvas, which also falls back to Simple when the
     * density is unknown.
     */
    fun generateCompareCanvas(
      goldenCanvas: UIImageRoboCanvas,
      newCanvas: UIImageRoboCanvas,
      newCanvasResize: Double = 1.0,
      useGrid: Boolean = false,
      oneDpPx: Float? = null,
      bigLineSpaceDp: Int? = 16,
      smallLineSpaceDp: Int? = 4,
      hasLabel: Boolean = true,
    ): UIImageRoboCanvas {
      // The golden is stored already scaled by resizeScale while the captured
      // actual is full size, so scale the actual down to match before composing
      // (the common pipeline compares the scaled actual). Mirrors AwtRoboCanvas,
      // which scales newImage by newCanvasResize.
      val scaledNew = if (newCanvasResize == 1.0) newCanvas else newCanvas.scaled(newCanvasResize)
      try {
        val sectionWidth = maxOf(goldenCanvas.width, scaledNew.width)
        val contentHeight = maxOf(goldenCanvas.height, scaledNew.height)
        return if (useGrid && oneDpPx != null && oneDpPx > 0f) {
          renderGridCanvas(
            goldenCanvas = goldenCanvas,
            newCanvas = scaledNew,
            sectionWidth = sectionWidth,
            contentHeight = contentHeight,
            oneDpPx = oneDpPx,
            bigLineSpaceDp = bigLineSpaceDp,
            smallLineSpaceDp = smallLineSpaceDp,
            hasLabel = hasLabel,
          )
        } else {
          renderSimpleCanvas(goldenCanvas, scaledNew, sectionWidth, contentHeight)
        }
      } finally {
        if (scaledNew !== newCanvas) scaledNew.release()
      }
    }

    private val diffRed = Color(255, 0, 0, 255)

    /** Writes a straight (un-premultiplied) [color] at ([x], [y]) into [out]. */
    private fun setPixel(out: ByteArray, totalWidth: Int, x: Int, y: Int, color: Color) {
      val base = (y * totalWidth + x) * 4
      out[base] = (color.r * 255f).roundToInt().coerceIn(0, 255).toByte()
      out[base + 1] = (color.g * 255f).roundToInt().coerceIn(0, 255).toByte()
      out[base + 2] = (color.b * 255f).roundToInt().coerceIn(0, 255).toByte()
      out[base + 3] = (color.a * 255f).roundToInt().coerceIn(0, 255).toByte()
    }

    /**
     * Straight-alpha "source over destination" blend of an ([sr], [sg], [sb])
     * color with fractional alpha [sa] (all 0..1) onto the pixel at ([x], [y]).
     * Used to overlay semi-transparent grid lines and labels.
     */
    private fun blendPixel(
      out: ByteArray,
      totalWidth: Int,
      x: Int,
      y: Int,
      sr: Float,
      sg: Float,
      sb: Float,
      sa: Float,
    ) {
      if (sa <= 0f) return
      val base = (y * totalWidth + x) * 4
      val dr = (out[base].toInt() and 0xFF) / 255f
      val dg = (out[base + 1].toInt() and 0xFF) / 255f
      val db = (out[base + 2].toInt() and 0xFF) / 255f
      val da = (out[base + 3].toInt() and 0xFF) / 255f
      val outA = sa + da * (1f - sa)
      if (outA <= 0f) return
      val outR = (sr * sa + dr * da * (1f - sa)) / outA
      val outG = (sg * sa + dg * da * (1f - sa)) / outA
      val outB = (sb * sa + db * da * (1f - sa)) / outA
      out[base] = (outR * 255f).roundToInt().coerceIn(0, 255).toByte()
      out[base + 1] = (outG * 255f).roundToInt().coerceIn(0, 255).toByte()
      out[base + 2] = (outB * 255f).roundToInt().coerceIn(0, 255).toByte()
      out[base + 3] = (outA * 255f).roundToInt().coerceIn(0, 255).toByte()
    }

    private fun compositeSections(
      out: ByteArray,
      totalWidth: Int,
      goldenCanvas: UIImageRoboCanvas,
      newCanvas: UIImageRoboCanvas,
      sectionWidth: Int,
      offsetX: Int,
      offsetY: Int,
    ) {
      val height = maxOf(goldenCanvas.height, newCanvas.height)
      for (y in 0 until height) {
        for (x in 0 until sectionWidth) {
          val golden = if (x < goldenCanvas.width && y < goldenCanvas.height) {
            goldenCanvas.getPixel(x, y)
          } else null
          val new = if (x < newCanvas.width && y < newCanvas.height) {
            newCanvas.getPixel(x, y)
          } else null
          if (golden != null) setPixel(out, totalWidth, offsetX + x, offsetY + y, golden)
          if (new != null) {
            setPixel(out, totalWidth, offsetX + sectionWidth * 2 + x, offsetY + y, new)
          }
          // Diff section: red where pixels differ (or exist in only one image).
          if (golden != new) {
            setPixel(out, totalWidth, offsetX + sectionWidth + x, offsetY + y, diffRed)
          }
        }
      }
    }

    private fun renderSimpleCanvas(
      goldenCanvas: UIImageRoboCanvas,
      newCanvas: UIImageRoboCanvas,
      sectionWidth: Int,
      height: Int,
    ): UIImageRoboCanvas {
      val totalWidth = sectionWidth * 3
      require(totalWidth.toLong() * height * 4 <= Int.MAX_VALUE) {
        "Comparison canvas ${totalWidth}x$height exceeds the supported pixel buffer size"
      }
      val out = ByteArray(totalWidth * height * 4)
      compositeSections(out, totalWidth, goldenCanvas, newCanvas, sectionWidth, 0, 0)
      return fromUnpremultipliedRgbaBytes(totalWidth, height, out)
    }

    private fun renderGridCanvas(
      goldenCanvas: UIImageRoboCanvas,
      newCanvas: UIImageRoboCanvas,
      sectionWidth: Int,
      contentHeight: Int,
      oneDpPx: Float,
      bigLineSpaceDp: Int?,
      smallLineSpaceDp: Int?,
      hasLabel: Boolean,
    ): UIImageRoboCanvas {
      val margin = (16 * oneDpPx).toInt().coerceAtLeast(1)
      val totalWidth = sectionWidth * 3 + margin * 2
      val totalHeight = contentHeight + margin * 2
      require(totalWidth.toLong() * totalHeight * 4 <= Int.MAX_VALUE) {
        "Comparison canvas ${totalWidth}x$totalHeight exceeds the supported pixel buffer size"
      }
      val out = ByteArray(totalWidth * totalHeight * 4)
      compositeSections(
        out, totalWidth, goldenCanvas, newCanvas, sectionWidth,
        offsetX = margin, offsetY = margin,
      )

      // Grid lines, matching the JVM colors: small = #33777777, big = #99777777.
      val lineGray = 0x77 / 255f
      smallLineSpaceDp?.let { drawGrid(out, totalWidth, totalHeight, it, oneDpPx, lineGray, 0x33 / 255f) }
      bigLineSpaceDp?.let { drawGrid(out, totalWidth, totalHeight, it, oneDpPx, lineGray, 0x99 / 255f) }

      if (hasLabel) {
        val fontSize = (12 * oneDpPx).toInt().coerceAtLeast(1)
        drawLabel(out, totalWidth, totalHeight, "Reference", margin, margin, fontSize, oneDpPx)
        drawLabel(out, totalWidth, totalHeight, "Diff", margin + sectionWidth, margin, fontSize, oneDpPx)
        drawLabel(out, totalWidth, totalHeight, "New", margin + sectionWidth * 2, margin, fontSize, oneDpPx)
      }
      return fromUnpremultipliedRgbaBytes(totalWidth, totalHeight, out)
    }

    private fun drawGrid(
      out: ByteArray,
      totalWidth: Int,
      totalHeight: Int,
      spaceDp: Int,
      oneDpPx: Float,
      gray: Float,
      alpha: Float,
    ) {
      val step = (spaceDp * oneDpPx).toInt().coerceAtLeast(1)
      var y = 0
      while (y < totalHeight) {
        for (x in 0 until totalWidth) blendPixel(out, totalWidth, x, y, gray, gray, gray, alpha)
        y += step
      }
      var x = 0
      while (x < totalWidth) {
        for (yy in 0 until totalHeight) blendPixel(out, totalWidth, x, yy, gray, gray, gray, alpha)
        x += step
      }
    }

    /**
     * Renders [text] with a translucent background rectangle into a temporary
     * canvas via UIKit text drawing, then alpha-composites it onto [out] at
     * ([destX], [bottomY]). [bottomY] is the label box's BOTTOM edge (a baseline
     * anchor), matching the JVM `drawStringWithBackgroundRect`, which treats
     * `y = margin` as the baseline and draws the box upward so its bottom lands
     * at the image top edge. The box top is `bottomY - boxHeight`, clamped to 0
     * so the label never extends off the top of the canvas (the JVM clips at the
     * canvas edge; clamping to 0 is the closest parity). The temporary context is
     * flipped so UIKit draws the glyphs upright relative to the top-first pixel
     * buffer.
     */
    private fun drawLabel(
      out: ByteArray,
      totalWidth: Int,
      totalHeight: Int,
      text: String,
      destX: Int,
      bottomY: Int,
      fontSize: Int,
      oneDpPx: Float,
    ) {
      val font = UIFont.boldSystemFontOfSize(fontSize.toDouble())
      val attributes = mapOf<Any?, Any>(
        NSFontAttributeName to font,
        NSForegroundColorAttributeName to UIColor.blackColor,
      )
      val nsText = text as NSString
      val (textWidth, textHeight) = nsText.sizeWithAttributes(attributes).useContents {
        width to height
      }
      val pad = (4 * oneDpPx).toInt().coerceAtLeast(1)
      val boxWidth = (textWidth.toInt() + pad * 2).coerceAtLeast(1)
      val boxHeight = (textHeight.toInt() + pad * 2).coerceAtLeast(1)
      val label = create(boxWidth, boxHeight)
      try {
        // Translucent background rectangle (#55999999), matching the JVM label.
        CGContextSetRGBFillColor(label.context, 0x99 / 255.0, 0x99 / 255.0, 0x99 / 255.0, 0x55 / 255.0)
        CGContextFillRect(label.context, CGRectMake(0.0, 0.0, boxWidth.toDouble(), boxHeight.toDouble()))
        // Flip so UIKit's top-left text origin matches the top-first buffer.
        CGContextTranslateCTM(label.context, 0.0, boxHeight.toDouble())
        CGContextScaleCTM(label.context, 1.0, -1.0)
        UIGraphicsPushContext(label.context)
        nsText.drawAtPoint(CGPointMake(pad.toDouble(), pad.toDouble()), attributes)
        UIGraphicsPopContext()

        // Anchor the box's bottom edge at bottomY, drawing upward. When the box
        // is taller than the space above bottomY, the top rows fall at negative
        // y and are clipped at the canvas top edge (matching how the JVM clips a
        // box whose top lands above y=0), keeping the bottom pinned at bottomY.
        val topY = bottomY - boxHeight
        for (ly in 0 until boxHeight) {
          val oy = topY + ly
          if (oy < 0) continue
          if (oy >= totalHeight) break
          for (lx in 0 until boxWidth) {
            val ox = destX + lx
            if (ox >= totalWidth) break
            val p = label.getPixel(lx, ly)
            blendPixel(out, totalWidth, ox, oy, p.r, p.g, p.b, p.a)
          }
        }
      } finally {
        label.release()
      }
    }
  }
}

/**
 * Adapts a [UIImageRoboCanvas] to dropbox/differ's [Image] interface, exposing
 * straight (un-premultiplied) RGBA pixels so that comparison results match the
 * JVM `DifferBufferedImage` implementation.
 */
@ExperimentalRoborazziApi
internal class DifferCGImage(
  private val canvas: UIImageRoboCanvas
) : Image {
  override val width: Int get() = canvas.width
  override val height: Int get() = canvas.height

  override fun getPixel(x: Int, y: Int): Color {
    if (x >= width || y >= height) {
      // Waiting for dropbox/differ to support size differences directly.
      return Color(0, 0, 0, 0)
    }
    return canvas.getPixel(x, y)
  }
}
