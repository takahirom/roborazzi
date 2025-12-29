@file:OptIn(InternalRoborazziApi::class)

package io.github.takahirom.roborazzi

import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import com.github.takahirom.roborazzi.CaptureResult
import com.github.takahirom.roborazzi.CaptureResults
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.getReportFileName
import com.github.takahirom.roborazzi.roborazziReportLog
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.github.takahirom.roborazzi.roborazziSystemPropertyProjectPath
import com.github.takahirom.roborazzi.roborazziSystemPropertyResultDirectory
import com.github.takahirom.roborazzi.roborazziSystemPropertyTaskType
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import platform.CoreFoundation.CFAbsoluteTimeGetCurrent
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGBitmapContextGetData
import platform.CoreGraphics.CGColorRenderingIntent
import platform.CoreGraphics.CGColorSpaceCreateWithName
import platform.CoreGraphics.CGColorSpaceGetName
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextSetFillColorWithColor
import platform.CoreGraphics.CGDataProviderCopyData
import platform.CoreGraphics.CGDataProviderCreateWithCFData
import platform.CoreGraphics.CGDataProviderRef
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageCreate
import platform.CoreGraphics.CGImageGetBitmapInfo
import platform.CoreGraphics.CGImageGetBitsPerPixel
import platform.CoreGraphics.CGImageGetBytesPerRow
import platform.CoreGraphics.CGImageGetColorSpace
import platform.CoreGraphics.CGImageGetDataProvider
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Little
import platform.CoreGraphics.kCGColorSpaceSRGB
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding
import platform.Foundation.writeToFile
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.abs
import platform.posix.memcpy

/**
 * This is a temporary implementation for iOS.
 * We need to transition these implementations to Kotlin Multiplatform and RoboCanvas.
 *
 * Here's the basic approach for the image format:
 * 1. Capture the image using:
 *    ```
 *    val colorSpace = CGColorSpaceCreateWithName(kCGColorSpaceSRGB)
 *    val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaFirst.value or kCGBitmapByteOrder32Little
 *    ```
 * 2. Convert the image to a comparable format:
 *    ```
 *    val colorSpace = CGColorSpaceCreateWithName(kCGColorSpaceSRGB)
 *    val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst.value or kCGBitmapByteOrder32Little
 *    ```
 * 3. For comparison tasks, load the image from a file and convert it to the format described in the second step.
 */
@OptIn(ExperimentalForeignApi::class)
private fun PixelMap.toCGDataProvider(): CGDataProviderRef? {
  val bytes = this.buffer
  val size = (this.width * this.height * 4).convert<ULong>()  // Assuming 4 bytes per pixel (RGBA)
  return memScoped {
    val bytesPointer = allocArray<ByteVar>(size.toInt())
    memcpy(bytesPointer, bytes.refTo(0), size)
    val data = CFDataCreate(null, bytesPointer.reinterpret(), size.convert()) ?: return null
    CGDataProviderCreateWithCFData(data)
  }
}

@OptIn(ExperimentalForeignApi::class)
private fun PixelMap.toUIImage(): UIImage? {
  memScoped {
    val dataProvider = this@toUIImage.toCGDataProvider()

    val colorSpace =  CGColorSpaceCreateWithName(kCGColorSpaceSRGB)
    val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaFirst.value or
      kCGBitmapByteOrder32Little

    val bytesPerRow = (this@toUIImage.width * 4).toULong()

    val imageRef = CGImageCreate(
      width = this@toUIImage.width.toULong(),
      height = this@toUIImage.height.toULong(),
      bitsPerComponent = 8.toULong(),
      bitsPerPixel = 32.toULong(),
      bytesPerRow = bytesPerRow,
      space = colorSpace,
      bitmapInfo = bitmapInfo,
      provider = dataProvider,
      decode = null,
      shouldInterpolate = true,
      intent = CGColorRenderingIntent.kCGRenderingIntentAbsoluteColorimetric
    )

    return if (imageRef != null) UIImage.imageWithCGImage(imageRef) else null
  }
}

@OptIn(ExperimentalForeignApi::class)
private fun convertImageFormat(image: UIImage): UIImage? {
  val cgImage = image.CGImage ?: return null

  val colorSpace =  CGColorSpaceCreateWithName(kCGColorSpaceSRGB)
  val width = CGImageGetWidth(cgImage)
  val height = CGImageGetHeight(cgImage)
  val bytesPerPixel = 4u
  val bytesPerRow = bytesPerPixel * width
  // We can't use kCGImageAlphaFirst here because it's not supported on iOS
  val bitmapInfo =
    CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst.value or kCGBitmapByteOrder32Little

  val context = CGBitmapContextCreate(null, width, height, 8u, bytesPerRow, colorSpace, bitmapInfo)
  context?.let {
    CGContextDrawImage(it, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), cgImage)
    val newCGImage = CGBitmapContextCreateImage(it)
    CGContextRelease(it)
    return newCGImage?.let { imgRef -> UIImage.imageWithCGImage(imgRef) }
  }

  return null
}

@OptIn(ExperimentalForeignApi::class)
private fun unpremultiplyAlpha(cgImage: CGImageRef): CGImageRef? {
  val width = CGImageGetWidth(cgImage)
  val height = CGImageGetHeight(cgImage)
  val colorSpace = CGImageGetColorSpace(cgImage) ?: return null
  val bytesPerRow = CGImageGetBytesPerRow(cgImage)
  val bitmapInfo = CGImageGetBitmapInfo(cgImage)

  val context = CGBitmapContextCreate(null, width, height, 8u, bytesPerRow, colorSpace, bitmapInfo)
    ?: return null

  CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), cgImage)
  val data = CGBitmapContextGetData(context)?.reinterpret<UByteVar>()

  if (data != null) {
    val pixelCount = width * height
    for (i in 0 until pixelCount.toInt()) {
      val baseIndex = i * 4
      val alpha = data[baseIndex + 3].toInt() and 0xFF
      if (alpha != 0) {
        data[baseIndex] = ((data[baseIndex].toInt() and 0xFF) * 255 / alpha).toByte().toUByte()
        data[baseIndex + 1] =
          ((data[baseIndex + 1].toInt() and 0xFF) * 255 / alpha).toByte().toUByte()
        data[baseIndex + 2] =
          ((data[baseIndex + 2].toInt() and 0xFF) * 255 / alpha).toByte().toUByte()
      }
    }
  }

  return CGBitmapContextCreateImage(context)
}

@OptIn(ExperimentalForeignApi::class) fun multiplyAlpha(cgImage: CGImageRef): CGImageRef? {
  val width = CGImageGetWidth(cgImage)
  val height = CGImageGetHeight(cgImage)
  val colorSpace =  CGColorSpaceCreateWithName(kCGColorSpaceSRGB)
  val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst.value or kCGBitmapByteOrder32Little
  val bytesPerRow = CGImageGetBytesPerRow(cgImage)

  val context = CGBitmapContextCreate(null, width, height, 8u, bytesPerRow, colorSpace, bitmapInfo)
    ?: return null

  // It seems that we don't need to unpremultiplyAlpha here because the image is premultiplied automatically
  CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), cgImage)

  return CGBitmapContextCreateImage(context)
}

// FIXME We want to use RoboCanvas here so we have to avoid using JVM APIs
@OptIn(ExperimentalForeignApi::class) private fun generateCompareImage(
  goldenImage: UIImage?,
  newImage: UIImage
): CGImageRef? {
  if (goldenImage == null) {
    roborazziReportLog("CompareImage Golden image is null")
    return newImage.CIImage?.CGImage
  }

  val goldenCgImage = goldenImage.CGImage!!
  val newCgImage = multiplyAlpha(newImage.CGImage!!)!!

  val goldenWidth = CGImageGetWidth(goldenCgImage)
  val newWidth = CGImageGetWidth(newCgImage)
  val sectionWidth = maxOf(goldenWidth, newWidth)
  val compareWidth = sectionWidth * 3u
  val goldenHeight = CGImageGetHeight(goldenCgImage)
  val newHeight = CGImageGetHeight(newCgImage)
  val height = maxOf(goldenHeight, newHeight)
  val colorSpace = CGImageGetColorSpace(newCgImage)
  val goldenBytePerRow = CGImageGetBytesPerRow(goldenCgImage)
  val newBytePerRow = CGImageGetBytesPerRow(newCgImage)
  val compareBytesPerRow = maxOf(goldenBytePerRow, newBytePerRow) * 3u
  val bitmapInfo = CGImageGetBitmapInfo(goldenCgImage)

  // reference, diff, new
  val context = CGBitmapContextCreate(
    null,
    compareWidth,
    height,
    8u,
    compareBytesPerRow,
    colorSpace,
    bitmapInfo
  )
  if (context == null) {
    roborazziReportLog("CompareImage Failed to create context")
    return null
  }

  // Diff
  val goldenDataProvider = CGImageGetDataProvider(goldenCgImage)!!
  val newDataProvider = CGImageGetDataProvider(newCgImage)!!

  val goldenData = CGDataProviderCopyData(goldenDataProvider)!!
  val newData = CGDataProviderCopyData(newDataProvider)!!

  val goldenPtr = CFDataGetBytePtr(goldenData)!!.reinterpret<UByteVar>()
  val newPtr = CFDataGetBytePtr(newData)!!.reinterpret<UByteVar>()
  CGContextSetFillColorWithColor(context, UIColor.redColor.CGColor)
  for (y in 1..height.toInt()) {
    if (goldenHeight.toInt() < y || newHeight.toInt() < y) {
      CGContextFillRect(
        context,
        CGRectMake(
          sectionWidth.toDouble(),
          height.toDouble() - y.toDouble(),
          sectionWidth.toDouble(),
          1.0
        )
      )
      continue
    }
    val yGoldenPixelOffset = (y - 1) * goldenBytePerRow.toInt()
    val yNewPixelOffset = (y - 1) * newBytePerRow.toInt()
    for (x in 0 until compareWidth.toInt() / 3) {
      if (goldenWidth.toInt() < x || newWidth.toInt() < x) {
        CGContextFillRect(
          context,
          CGRectMake(
            x.toDouble() + sectionWidth.toDouble(),
            height.toDouble() - y.toDouble(),
            1.0,
            1.0
          )
        )
        continue
      }
      val goldenPixelIndex = yGoldenPixelOffset + x * 4
      val newPixelIndex = yNewPixelOffset + x * 4
      if (
        isTheSamePixel(
          goldenPtr = goldenPtr,
          goldenPixelIndex = goldenPixelIndex,
          newPtr = newPtr,
          newPixelIndex = newPixelIndex,
        )
      ) {
        CGContextFillRect(
          context,
          CGRectMake(
            x.toDouble() + sectionWidth.toDouble(),
            height.toDouble() - y.toDouble(),
            1.0,
            1.0
          )
        )
      }
    }
  }
  CFRelease(goldenData)
  CFRelease(newData)

  // Reference
  CGContextDrawImage(
    context,
    CGRectMake(
      x = 0.0,
      y = -goldenHeight.toDouble() + height.toDouble(),
      width = goldenWidth.toDouble(),
      height = goldenHeight.toDouble()
    ),
    goldenCgImage
  )

  // New
  CGContextDrawImage(
    context,
    CGRectMake(
      x = compareWidth.toDouble() * 2 / 3,
      y = -newHeight.toDouble() + height.toDouble(),
      width = newWidth.toDouble(),
      height = newHeight.toDouble()
    ),
    newCgImage
  )

  val cgBitmapContextCreateImage = CGBitmapContextCreateImage(context)
  if (cgBitmapContextCreateImage == null) {
    roborazziReportLog("CompareImage Failed to create image")
  }
  return cgBitmapContextCreateImage
}

@OptIn(ExperimentalForeignApi::class) private fun hasChangedPixel(
  goldenImage: UIImage,
  newImage: UIImage
): Boolean {
  val goldenCgImage = goldenImage.CGImage!!
  val newCgImage = multiplyAlpha(newImage.CGImage!!)!!

  if (CGImageGetWidth(goldenCgImage) != CGImageGetWidth(newCgImage) ||
    CGImageGetHeight(goldenCgImage) != CGImageGetHeight(newCgImage)
  ) return true

  val goldenBytesPerRow = CGImageGetBytesPerRow(goldenCgImage)
  val newBytesPerRow = CGImageGetBytesPerRow(newCgImage)

  val goldenDataProvider = CGImageGetDataProvider(goldenCgImage)!!
  val newDataProvider = CGImageGetDataProvider(newCgImage)!!

  val goldenData = CGDataProviderCopyData(goldenDataProvider)!!
  val newData = CGDataProviderCopyData(newDataProvider)!!

  val goldenPtr = CFDataGetBytePtr(goldenData)!!.reinterpret<UByteVar>()
  val newPtr = CFDataGetBytePtr(newData)!!.reinterpret<UByteVar>()

  val goldenImageWidth = CGImageGetWidth(goldenCgImage)
  val goldenImageHeight = CGImageGetHeight(goldenCgImage)
  // Waiting for https://github.com/dropbox/differ/pull/16
  try {
    for (y in 0 until goldenImageHeight.toInt()) {
      val yGoldenPixelOffset = y * goldenBytesPerRow.toInt()
      val yNewPixelOffset = y * newBytesPerRow.toInt()
      for (x in 0 until goldenImageWidth.toInt()) {
        val goldenPixelIndex = yGoldenPixelOffset + x * 4
        val newPixelIndex = yNewPixelOffset + x * 4
        if (
          isTheSamePixel(
            goldenPtr = goldenPtr,
            goldenPixelIndex = goldenPixelIndex,
            newPtr = newPtr,
            newPixelIndex = newPixelIndex,
          )
        ) {
          roborazziReportLog("Pixel changed at ($x, $y) from rgba(${goldenPtr[goldenPixelIndex]}, ${goldenPtr[goldenPixelIndex + 1]}, ${goldenPtr[goldenPixelIndex + 2]}, ${goldenPtr[goldenPixelIndex + 3]}) to rgba(${newPtr[newPixelIndex]}, ${newPtr[newPixelIndex + 1]}, ${newPtr[newPixelIndex + 2]}, ${newPtr[newPixelIndex + 3]})")
          val stringBuilder = StringBuilder()

          // properties
          stringBuilder.appendLine(
            "reference CGImageGetColorSpace" + CFBridgingRelease(
              CGColorSpaceGetName(
                CGImageGetColorSpace(
                  goldenCgImage
                )
              )
            )
          )
          stringBuilder.appendLine(
            "new CGImageGetColorSpace" + CFBridgingRelease(
              CGColorSpaceGetName(
                CGImageGetColorSpace(
                  newCgImage
                )
              )
            )
          )
          stringBuilder.appendLine(
            "reference CGImageGetBitmapInfo" + CGImageGetBitmapInfo(
              goldenCgImage
            )
          )
          stringBuilder.appendLine("new CGImageGetBitmapInfo" + CGImageGetBitmapInfo(newCgImage))
          stringBuilder.appendLine(
            "reference CGImageGetBitsPerPixel" + CGImageGetBitsPerPixel(
              goldenCgImage
            )
          )
          stringBuilder.appendLine("new CGImageGetBitsPerPixel" + CGImageGetBitsPerPixel(newCgImage))
          stringBuilder.appendLine(
            "reference CGImageGetBytesPerRow" + CGImageGetBytesPerRow(
              goldenCgImage
            )
          )
          stringBuilder.appendLine("new CGImageGetBytesPerRow" + CGImageGetBytesPerRow(newCgImage))
          roborazziReportLog(stringBuilder.toString())

          return true
        }
      }
    }

    return false
  } finally {
    CFRelease(goldenData)
    CFRelease(newData)
  }
}

@OptIn(ExperimentalForeignApi::class)
private inline fun isTheSamePixel(
  goldenPtr: CPointer<UByteVar>,
  goldenPixelIndex: Int,
  newPtr: CPointer<UByteVar>,
  newPixelIndex: Int,
  colorDistance: Int = 2
): Boolean {
  return abs((goldenPtr[goldenPixelIndex] - newPtr[newPixelIndex]).toInt()) > colorDistance ||
    abs((goldenPtr[goldenPixelIndex + 1] - newPtr[newPixelIndex + 1]).toInt()) > colorDistance ||
    abs((goldenPtr[goldenPixelIndex + 2] - newPtr[newPixelIndex + 2]).toInt()) > colorDistance ||
    abs((goldenPtr[goldenPixelIndex + 3] - newPtr[newPixelIndex + 3]).toInt()) > colorDistance
}

fun String.toNsData(): NSData {
  @Suppress("CAST_NEVER_SUCCEEDS")
  return (this as NSString).dataUsingEncoding(NSUTF8StringEncoding)!!
}

/**
 * TODO: Commonize RoborazziOptions with JVM and iOS
 */
@ExperimentalRoborazziApi
class RoborazziOptions(
  val taskType: RoborazziTaskType = roborazziSystemPropertyTaskType(),
  val compareOptions: CompareOptions = CompareOptions(),
)

@OptIn(ExperimentalRoborazziApi::class)
data class CompareOptions(
  val outputDirectoryPath: String = roborazziSystemPropertyOutputDirectory(),
)

fun String.toAbsolutePath(projectPath: String): String {
  return if (this.startsWith("/")) {
    this
  } else {
    "$projectPath/$this"
  }
}

@ExperimentalRoborazziApi
@OptIn(ExperimentalTestApi::class, ExperimentalForeignApi::class, ExperimentalRoborazziApi::class)
fun SemanticsNodeInteraction.captureRoboImage(
  composeUiTest: ComposeUiTest,
  filePath: String,
  roborazziOptions: RoborazziOptions = RoborazziOptions(),
) {
  val projectDir = roborazziSystemPropertyProjectPath()
  val newImage: UIImage = captureToImage().toPixelMap().toUIImage()!!
  val outputDir = roborazziSystemPropertyOutputDirectory()
  // This will be changed to use fileWithRecordFilePathStrategy
  val baseOutputPath = outputDir.toAbsolutePath(projectDir)
  val compareDir = roborazziOptions.compareOptions.outputDirectoryPath
  val compareDirPath = compareDir.toAbsolutePath(projectDir)
  val resultsDir = roborazziSystemPropertyResultDirectory().toAbsolutePath(projectDir)

  val roborazziTaskType = roborazziOptions.taskType
  val ext = filePath.substringAfterLast(".")
  val filePathWithOutExtension = filePath.substringBeforeLast(".")
  val nameWithoutExtension = filePathWithOutExtension.substringAfterLast("/")

  val actualFilePath = "$compareDirPath/${filePathWithOutExtension}_actual.$ext"
  val compareFilePath = "$compareDirPath/${filePathWithOutExtension}_compare.$ext"
  val goldenFilePath = "$baseOutputPath/$filePath"
  when (roborazziTaskType) {
    RoborazziTaskType.None -> return
    RoborazziTaskType.Record -> {
      writeImage(newImage, goldenFilePath)
      val result = CaptureResult.Recorded(
        goldenFile = goldenFilePath,
        timestampNs = getNanoTime(),
        contextData = emptyMap()
      )
      writeJson(result, resultsDir, nameWithoutExtension)
    }

    RoborazziTaskType.Compare -> {
      val goldenImage = loadGoldenImage(goldenFilePath)
      if (goldenImage == null) {
        writeImage(newImage, actualFilePath)
        writeImage(
          newImage = generateCompareImage(goldenImage, newImage)?.let { UIImage(it) } ?: newImage,
          path = compareFilePath
        )
        val result = CaptureResult.Added(
          compareFile = compareFilePath,
          actualFile = actualFilePath,
          goldenFile = goldenFilePath,
          timestampNs = getNanoTime(),
          aiAssertionResults = null,
          contextData = emptyMap()
        )
        writeJson(result, resultsDir, nameWithoutExtension)
        return
      }
      if (hasChangedPixel(goldenImage, newImage)) {
        writeImage(newImage, actualFilePath)
        writeImage(
          newImage = generateCompareImage(goldenImage, newImage)?.let { UIImage(it) } ?: newImage,
          path = compareFilePath
        )
        val result = CaptureResult.Changed(
          compareFile = compareFilePath,
          actualFile = actualFilePath,
          goldenFile = goldenFilePath,
          timestampNs = getNanoTime(),
          diffPercentage = null,
          aiAssertionResults = null,
          contextData = emptyMap()
        )
        writeJson(result, resultsDir, nameWithoutExtension)
        return
      }
      val result = CaptureResult.Unchanged(
        goldenFile = goldenFilePath,
        timestampNs = getNanoTime(),
        contextData = emptyMap()
      )
      writeJson(result, resultsDir, nameWithoutExtension)
    }

    RoborazziTaskType.Verify -> {
      val goldenImage = loadGoldenImage(goldenFilePath)
      if (goldenImage == null) {
        writeImage(newImage, actualFilePath)
        writeImage(
          newImage = generateCompareImage(goldenImage, newImage)?.let { UIImage(it) } ?: newImage,
          path = compareFilePath
        )
        val result = CaptureResult.Added(
          compareFile = compareFilePath,
          actualFile = actualFilePath,
          goldenFile = goldenFilePath,
          timestampNs = getNanoTime(),
          aiAssertionResults = null,
          contextData = emptyMap()
        )
        writeJson(result, resultsDir, nameWithoutExtension)
        throw AssertionError("Golden file not found for $filePath")
      }
      if (hasChangedPixel(goldenImage, newImage)) {
        writeImage(newImage, actualFilePath)
        writeImage(
          newImage = generateCompareImage(goldenImage, newImage)?.let { UIImage(it) } ?: newImage,
          path = compareFilePath
        )
        val result = CaptureResult.Changed(
          compareFile = compareFilePath,
          actualFile = actualFilePath,
          goldenFile = goldenFilePath,
          timestampNs = getNanoTime(),
          diffPercentage = null,
          aiAssertionResults = null,
          contextData = emptyMap()
        )
        writeJson(result, resultsDir, nameWithoutExtension)
        throw AssertionError("Pixel changed for $filePath")
      }
      val result = CaptureResult.Unchanged(
        goldenFile = goldenFilePath,
        timestampNs = getNanoTime(),
        contextData = emptyMap()
      )
      writeJson(result, resultsDir, nameWithoutExtension)
    }

    RoborazziTaskType.VerifyAndRecord -> {
      val goldenImage = loadGoldenImage(goldenFilePath)
      if (goldenImage == null) {
        writeImage(newImage, goldenFilePath)
        writeImage(
          newImage = generateCompareImage(goldenImage, newImage)?.let { UIImage(it) } ?: newImage,
          path = compareFilePath
        )
        val result = CaptureResult.Added(
          compareFile = goldenFilePath,
          actualFile = goldenFilePath,
          goldenFile = goldenFilePath,
          timestampNs = getNanoTime(),
          aiAssertionResults = null,
          contextData = emptyMap()
        )
        writeJson(result, resultsDir, nameWithoutExtension)
        throw AssertionError("Golden file not found for $filePath")
      }
      if (hasChangedPixel(goldenImage, newImage)) {
        writeImage(newImage, goldenFilePath)
        writeImage(
          newImage = generateCompareImage(goldenImage, newImage)?.let { UIImage(it) } ?: newImage,
          path = compareFilePath
        )
        val result = CaptureResult.Changed(
          compareFile = goldenFilePath,
          actualFile = goldenFilePath,
          goldenFile = goldenFilePath,
          timestampNs = getNanoTime(),
          diffPercentage = null,
          aiAssertionResults = null,
          contextData = emptyMap()
        )
        writeJson(result, resultsDir, nameWithoutExtension)
        throw AssertionError("Pixel changed for $filePath")
      }
      val result = CaptureResult.Unchanged(
        goldenFile = goldenFilePath,
        timestampNs = getNanoTime(),
        contextData = emptyMap()
      )
      writeJson(result, resultsDir, nameWithoutExtension)
    }

    RoborazziTaskType.CompareAndRecord -> {
      val goldenImage = loadGoldenImage(goldenFilePath)
      if (goldenImage == null) {
        writeImage(newImage, goldenFilePath)
        writeImage(
          newImage = generateCompareImage(goldenImage, newImage)?.let { UIImage(it) } ?: newImage,
          path = compareFilePath
        )
        val result = CaptureResult.Added(
          compareFile = goldenFilePath,
          actualFile = goldenFilePath,
          goldenFile = goldenFilePath,
          timestampNs = getNanoTime(),
          aiAssertionResults = null,
          contextData = emptyMap()
        )
        writeJson(result, resultsDir, nameWithoutExtension)
        return
      }
      if (hasChangedPixel(goldenImage, newImage)) {
        writeImage(newImage, goldenFilePath)
        writeImage(
          newImage = generateCompareImage(goldenImage, newImage)?.let { UIImage(it) } ?: newImage,
          path = compareFilePath
        )
        val result = CaptureResult.Changed(
          compareFile = goldenFilePath,
          actualFile = goldenFilePath,
          goldenFile = goldenFilePath,
          timestampNs = getNanoTime(),
          diffPercentage = null,
          aiAssertionResults = null,
          contextData = emptyMap()
        )
        writeJson(result, resultsDir, nameWithoutExtension)
        return
      }
      val result = CaptureResult.Unchanged(
        goldenFile = goldenFilePath,
        timestampNs = getNanoTime(),
        contextData = emptyMap()
      )
      writeJson(result, resultsDir, nameWithoutExtension)
    }
  }
}

@OptIn(ExperimentalForeignApi::class)
private fun writeImage(newImage: UIImage, path: String) {
  val parentPath = path.substringBeforeLast("/")
  if (parentPath.isNotEmpty() && parentPath != path) {
    val success = NSFileManager.defaultManager.createDirectoryAtPath(
      parentPath,
      withIntermediateDirectories = true,
      attributes = null,
      error = null
    )
    if (!success) {
      roborazziReportLog("Failed to create directory at $parentPath")
    }
  }
  val writeSuccess = UIImagePNGRepresentation(newImage)!!.writeToFile(
    path,
    true
  )
  if (writeSuccess) {
    roborazziReportLog("Image is saved $path")
  } else {
    roborazziReportLog("Failed to write image to $path")
  }
}

private fun loadGoldenImage(
  filePath: String
): UIImage? {
  if (!NSFileManager.defaultManager.fileExistsAtPath(filePath)) {
    return null
  }
  @Suppress("USELESS_CAST")
  val image: UIImage? = UIImage(filePath) as UIImage?
  if (image == null) {
    roborazziReportLog("can't load reference image from $filePath")
  }
  val goldenImage = image ?.let { convertImageFormat(it) }
  if (goldenImage == null) {
    roborazziReportLog("can't convert reference image from $filePath")
  }
  return goldenImage
}

@OptIn(ExperimentalForeignApi::class)
private fun writeJson(
  result: CaptureResult,
  resultsDir: String,
  nameWithoutExtension: String
) {
  if (resultsDir.isNotEmpty()) {
    val success = NSFileManager.defaultManager.createDirectoryAtPath(
      resultsDir,
      withIntermediateDirectories = true,
      attributes = null,
      error = null
    )
    if (!success) {
      roborazziReportLog("Failed to create directory at $resultsDir")
    }
  }
  val module = SerializersModule {
    polymorphic(
      CaptureResult::class,
      CaptureResult.Recorded::class,
      CaptureResult.Recorded.serializer()
    )
    polymorphic(
      CaptureResult::class,
      CaptureResult.Added::class,
      CaptureResult.Added.serializer()
    )
    polymorphic(
      CaptureResult::class,
      CaptureResult.Changed::class,
      CaptureResult.Changed.serializer()
    )
    polymorphic(
      CaptureResult::class,
      CaptureResult.Unchanged::class,
      CaptureResult.Unchanged.serializer()
    )
  }
  val json = Json(CaptureResults.json) {
    serializersModule = module
  }
  val reportFileName = getReportFileName(
    absolutePath = resultsDir,
    timestampNs = result.timestampNs,
    nameWithoutExtension = nameWithoutExtension
  )
  val writeSuccess = json.encodeToJsonElement(PolymorphicSerializer(CaptureResult::class), result)
    .toString()
    .toNsData()
    .writeToFile(path = reportFileName, atomically = true)

  if (writeSuccess) {
    roborazziReportLog("Report file is saved $reportFileName")
  } else {
    roborazziReportLog("Failed to write report to $reportFileName")
  }
}

private fun getNanoTime(): Long {
  return (CFAbsoluteTimeGetCurrent() * 1e6).toLong()
}
