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
import platform.CoreGraphics.kCGBitmapByteOrder32Little
import platform.CoreGraphics.kCGColorSpaceSRGB
import platform.Foundation.NSFileManager
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation

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
    // Un-premultiply.
    val sr = (r * 255 / a).coerceAtMost(255)
    val sg = (g * 255 / a).coerceAtMost(255)
    val sb = (b * 255 / a).coerceAtMost(255)
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
      if (sourceImage != null) {
        CGContextDrawImage(
          canvas.context,
          CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()),
          sourceImage
        )
        CGImageRelease(sourceImage)
      }
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
     * Produces a side-by-side comparison canvas laid out as
     * `reference | diff | new`. Differing pixels in the middle (diff) section
     * are highlighted in red. This mirrors the JVM "Simple" comparison style;
     * grid lines and text labels (ComparisonStyle.Grid) are out of scope.
     */
    fun generateCompareCanvas(
      goldenCanvas: UIImageRoboCanvas,
      newCanvas: UIImageRoboCanvas,
    ): UIImageRoboCanvas {
      val goldenWidth = goldenCanvas.width
      val goldenHeight = goldenCanvas.height
      val newWidth = newCanvas.width
      val newHeight = newCanvas.height
      val sectionWidth = maxOf(goldenWidth, newWidth)
      val height = maxOf(goldenHeight, newHeight)
      val totalWidth = sectionWidth * 3

      val out = ByteArray(totalWidth * height * 4)
      fun setPixel(x: Int, y: Int, color: Color) {
        val base = (y * totalWidth + x) * 4
        out[base] = (color.r * 255f).toInt().toByte()
        out[base + 1] = (color.g * 255f).toInt().toByte()
        out[base + 2] = (color.b * 255f).toInt().toByte()
        out[base + 3] = (color.a * 255f).toInt().toByte()
      }

      val red = Color(255, 0, 0, 255)
      for (y in 0 until height) {
        for (x in 0 until sectionWidth) {
          val inGolden = x < goldenWidth && y < goldenHeight
          val inNew = x < newWidth && y < newHeight
          val golden = if (inGolden) goldenCanvas.getPixel(x, y) else null
          val new = if (inNew) newCanvas.getPixel(x, y) else null
          // Reference section
          if (golden != null) setPixel(x, y, golden)
          // New section
          if (new != null) setPixel(x + sectionWidth * 2, y, new)
          // Diff section: red where pixels differ (or exist in only one image).
          if (golden != new) {
            setPixel(x + sectionWidth, y, red)
          }
        }
      }
      return fromUnpremultipliedRgbaBytes(totalWidth, height, out)
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
