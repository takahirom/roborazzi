package io.github.takahirom.roborazzi

import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.getReportFileName
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.github.takahirom.roborazzi.roborazziSystemPropertyProjectPath
import com.github.takahirom.roborazzi.roborazziSystemPropertyResultDirectory
import com.github.takahirom.roborazzi.roborazziSystemPropertyTaskType
import kotlinx.cinterop.ByteVar
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
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGBitmapContextGetData
import platform.CoreGraphics.CGColorRenderingIntent
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceGetName
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextMoveToPoint
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

    val colorSpace = CGColorSpaceCreateDeviceRGB()
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

  val colorSpace = CGColorSpaceCreateDeviceRGB()
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
    return newCGImage?.let { UIImage.imageWithCGImage(it) }
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
  if (context == null) return null

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

@OptIn(ExperimentalForeignApi::class) private fun generateCompareImage(
  goldenImage: UIImage?,
  newImage: UIImage
): CGImageRef? {
  if (goldenImage == null) return newImage.CIImage?.CGImage

  val goldenCgImage = unpremultiplyAlpha(goldenImage.CGImage!!)!!
  val newCgImage = newImage.CGImage!!

  val compareWidth = CGImageGetWidth(goldenCgImage) * 3u
  val sectionWidth = CGImageGetWidth(goldenCgImage).toInt()
  val height = CGImageGetHeight(goldenCgImage)
  val colorSpace = CGImageGetColorSpace(newCgImage)
  val compareBytesPerRow = CGImageGetBytesPerRow(goldenCgImage) * 3u
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
  if (context == null) return null

  // Diff
  val goldenRef: CFDataRef? = CGDataProviderCopyData(CGImageGetDataProvider(goldenCgImage))
  val newRef: CFDataRef? = CGDataProviderCopyData(CGImageGetDataProvider(newCgImage))
  val goldenData = CFDataGetBytePtr(goldenRef)!!.reinterpret<UByteVar>()
  val newData = CFDataGetBytePtr(newRef)!!.reinterpret<UByteVar>()
  CGContextSetFillColorWithColor(context, UIColor.redColor.CGColor)
  for (y in 0 until height.toInt()) {
    for (x in 0 until compareWidth.toInt() / 3) {
      CGContextMoveToPoint(context, sectionWidth.toDouble(), 0.0)
      val goldenPixelIndex = y * (compareBytesPerRow.toInt() / 3) + x * 4
      val newPixelIndex = y * (compareBytesPerRow.toInt() / 3) + x * 4
      val colorDistance = 2
      if (
        abs((goldenData[goldenPixelIndex] - newData[newPixelIndex]).toInt()) > colorDistance ||
        abs((goldenData[goldenPixelIndex + 1] - newData[newPixelIndex + 1]).toInt()) > colorDistance ||
        abs((goldenData[goldenPixelIndex + 2] - newData[newPixelIndex + 2]).toInt()) > colorDistance ||
        abs((goldenData[goldenPixelIndex + 3] - newData[newPixelIndex + 3]).toInt()) > colorDistance
      ) {
        CGContextFillRect(
          context,
          CGRectMake(x.toDouble() + sectionWidth, height.toDouble() - y.toDouble(), 1.0, 1.0)
        )
      }
    }
  }
  CFRelease(goldenRef)
  CFRelease(newRef)

  // Reference
  CGContextDrawImage(
    context,
    CGRectMake(0.0, 0.0, sectionWidth.toDouble(), height.toDouble()),
    goldenCgImage
  )

  // New
  CGContextDrawImage(
    context,
    CGRectMake(compareWidth.toDouble() * 2 / 3, 0.0, sectionWidth.toDouble(), height.toDouble()),
    newCgImage
  )


  return CGBitmapContextCreateImage(context)
}

@OptIn(ExperimentalForeignApi::class) private fun hasChangedPixel(
  oldImage: UIImage,
  newImage: UIImage
): Boolean {
  val oldCgImage = unpremultiplyAlpha(oldImage.CGImage!!)!!
  val newCgImage = newImage.CGImage!!

  if (CGImageGetWidth(oldCgImage) != CGImageGetWidth(newCgImage) ||
    CGImageGetHeight(oldCgImage) != CGImageGetHeight(newCgImage)
  ) return true

  val oldBytesPerRow = CGImageGetBytesPerRow(oldCgImage)
  val newBytesPerRow = CGImageGetBytesPerRow(newCgImage)

  val oldDataProvider = CGImageGetDataProvider(oldCgImage)!!
  val newDataProvider = CGImageGetDataProvider(newCgImage)!!

  val oldData = CGDataProviderCopyData(oldDataProvider)!!
  val newData = CGDataProviderCopyData(newDataProvider)!!

  val oldPtr = CFDataGetBytePtr(oldData)!!.reinterpret<UByteVar>()
  val newPtr = CFDataGetBytePtr(newData)!!.reinterpret<UByteVar>()

  val width = CGImageGetWidth(oldCgImage)
  val height = CGImageGetHeight(oldCgImage)
  // Waiting for https://github.com/dropbox/differ/pull/16
  for (y in 0 until height.toInt()) {
    for (x in 0 until width.toInt()) {
      val oldPixelIndex = y * oldBytesPerRow.toInt() + x * 4
      val newPixelIndex = y * newBytesPerRow.toInt() + x * 4
      // unpremultiplyAlpha can cause a little error
      val colorDistance = 2
      if (
        abs((oldPtr[oldPixelIndex] - newPtr[newPixelIndex]).toInt()) > colorDistance ||
        abs((oldPtr[oldPixelIndex + 1] - newPtr[newPixelIndex + 1]).toInt()) > colorDistance ||
        abs((oldPtr[oldPixelIndex + 2] - newPtr[newPixelIndex + 2]).toInt()) > colorDistance ||
        abs((oldPtr[oldPixelIndex + 3] - newPtr[newPixelIndex + 3]).toInt()) > colorDistance
      ) {
        println("Pixel changed at ($x, $y) from rgba(${oldPtr[oldPixelIndex]}, ${oldPtr[oldPixelIndex + 1]}, ${oldPtr[oldPixelIndex + 2]}, ${oldPtr[oldPixelIndex + 3]}) to rgba(${newPtr[newPixelIndex]}, ${newPtr[newPixelIndex + 1]}, ${newPtr[newPixelIndex + 2]}, ${newPtr[newPixelIndex + 3]})")
        val stringBuilder = StringBuilder()

        // properties
        stringBuilder.appendLine(
          "old CGImageGetColorSpace" + CFBridgingRelease(
            CGColorSpaceGetName(
              CGImageGetColorSpace(
                oldCgImage
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
        stringBuilder.appendLine("old CGImageGetBitmapInfo" + CGImageGetBitmapInfo(oldCgImage))
        stringBuilder.appendLine("new CGImageGetBitmapInfo" + CGImageGetBitmapInfo(newCgImage))
        stringBuilder.appendLine("old CGImageGetBitsPerPixel" + CGImageGetBitsPerPixel(oldCgImage))
        stringBuilder.appendLine("new CGImageGetBitsPerPixel" + CGImageGetBitsPerPixel(newCgImage))
        stringBuilder.appendLine("old CGImageGetBytesPerRow" + CGImageGetBytesPerRow(oldCgImage))
        stringBuilder.appendLine("new CGImageGetBytesPerRow" + CGImageGetBytesPerRow(newCgImage))
        println(stringBuilder.toString())

        return true
      }
    }
  }

  return false
}

fun String.data(): NSData {
  return (this as NSString).dataUsingEncoding(NSUTF8StringEncoding)!!
}

@ExperimentalRoborazziApi
@OptIn(ExperimentalTestApi::class, ExperimentalForeignApi::class, ExperimentalRoborazziApi::class)
fun ComposeUiTest.captureRoboImage(
  semanticsNodeInteraction: SemanticsNodeInteraction,
  filePath: String,
) {
  val projectDir = roborazziSystemPropertyProjectPath()
  // SemanticsNodeInteraction.captureToImage() causes segmentation fault
  // So we use SkikoComposeUiTest.captureToImage() instead
  // But we can't capture node image with SkikoComposeUiTest.captureToImage()
  // FIXME: We need to find a way to capture node image with SemanticsNodeInteraction.captureToImage()
  val newImage: UIImage = semanticsNodeInteraction.captureToImage().toPixelMap().toUIImage()!!
  val outputDir = roborazziSystemPropertyOutputDirectory()
  val baseOutputPath = "$projectDir/$outputDir/"
  val resultsDir = "$projectDir/${roborazziSystemPropertyResultDirectory()}"

  val roborazziTaskType = roborazziSystemPropertyTaskType()
  val ext = filePath.substringAfterLast(".")
  val filePathWithOutExtension = filePath.substringBeforeLast(".")
  val nameWithoutExtension = filePathWithOutExtension.substringAfterLast("/")

  val actualFilePath = "$baseOutputPath${filePathWithOutExtension}_actual.$ext"
  val compareFilePath = "$baseOutputPath${filePathWithOutExtension}_compare.$ext"
  val goldenFilePath = baseOutputPath + filePath
  when (roborazziTaskType) {
    RoborazziTaskType.None -> return
    RoborazziTaskType.Record -> {
      writeImage(newImage, goldenFilePath)
    }

    RoborazziTaskType.Compare -> {
      val oldImage = loadOldImage(baseOutputPath, filePath)
      if (oldImage == null) {
        writeImage(newImage, actualFilePath)
        writeImage(
          newImage = generateCompareImage(oldImage, newImage)?.let { UIImage(it) } ?: newImage,
          path = compareFilePath
        )
        val result = CaptureResult.Added(
          compareFile = actualFilePath,
          actualFile = actualFilePath,
          goldenFile = "$baseOutputPath$filePath",
          timestampNs = CFAbsoluteTimeGetCurrent().toLong(),
          contextData = emptyMap()
        )
        writeJson(result, resultsDir, nameWithoutExtension)
        return
      }
      if (hasChangedPixel(oldImage, newImage)) {
        writeImage(newImage, actualFilePath)
        writeImage(
          newImage = generateCompareImage(oldImage, newImage)?.let { UIImage(it) } ?: newImage,
          path = compareFilePath
        )
        val result = CaptureResult.Changed(
          compareFile = actualFilePath,
          actualFile = actualFilePath,
          goldenFile = "$baseOutputPath$filePath",
          timestampNs = CFAbsoluteTimeGetCurrent().toLong(),
          contextData = emptyMap()
        )
        writeJson(result, resultsDir, nameWithoutExtension)
        return
      }
      val result = CaptureResult.Unchanged(
        goldenFile = "$baseOutputPath$filePath",
        timestampNs = CFAbsoluteTimeGetCurrent().toLong(),
        contextData = emptyMap()
      )
      writeJson(result, resultsDir, nameWithoutExtension)
    }

    RoborazziTaskType.Verify -> {
      val oldImage = loadOldImage(baseOutputPath, filePath)
      if (oldImage == null) {
        writeImage(newImage, actualFilePath)
        writeImage(
          newImage = generateCompareImage(oldImage, newImage)?.let { UIImage(it) } ?: newImage,
          path = compareFilePath
        )
        val result = CaptureResult.Added(
          compareFile = actualFilePath,
          actualFile = actualFilePath,
          goldenFile = "$baseOutputPath$filePath",
          timestampNs = CFAbsoluteTimeGetCurrent().toLong(),
          contextData = emptyMap()
        )
        writeJson(result, resultsDir, nameWithoutExtension)
        throw AssertionError("Golden file not found for $filePath")
      }
      if (hasChangedPixel(oldImage, newImage)) {
        writeImage(newImage, actualFilePath)
        writeImage(
          newImage = generateCompareImage(oldImage, newImage)?.let { UIImage(it) } ?: newImage,
          path = compareFilePath
        )
        val result = CaptureResult.Changed(
          compareFile = actualFilePath,
          actualFile = actualFilePath,
          goldenFile = "$baseOutputPath$filePath",
          timestampNs = CFAbsoluteTimeGetCurrent().toLong(),
          contextData = emptyMap()
        )
        writeJson(result, resultsDir, nameWithoutExtension)
        throw AssertionError("Pixel changed for $filePath")
      }
      val result = CaptureResult.Unchanged(
        goldenFile = "$baseOutputPath$filePath",
        timestampNs = CFAbsoluteTimeGetCurrent().toLong(),
        contextData = emptyMap()
      )
      writeJson(result, resultsDir, nameWithoutExtension)
    }

    RoborazziTaskType.VerifyAndRecord -> {
      val oldImage = loadOldImage(baseOutputPath, filePath)
      if (oldImage == null) {
        writeImage(newImage, goldenFilePath)
        writeImage(
          newImage = generateCompareImage(oldImage, newImage)?.let { UIImage(it) } ?: newImage,
          path = compareFilePath
        )
        val result = CaptureResult.Added(
          compareFile = goldenFilePath,
          actualFile = goldenFilePath,
          goldenFile = "$baseOutputPath$filePath",
          timestampNs = CFAbsoluteTimeGetCurrent().toLong(),
          contextData = emptyMap()
        )
        writeJson(result, resultsDir, nameWithoutExtension)
        throw AssertionError("Golden file not found for $filePath")
      }
      if (hasChangedPixel(oldImage, newImage)) {
        writeImage(newImage, goldenFilePath)
        writeImage(
          newImage = generateCompareImage(oldImage, newImage)?.let { UIImage(it) } ?: newImage,
          path = compareFilePath
        )
        val result = CaptureResult.Changed(
          compareFile = goldenFilePath,
          actualFile = goldenFilePath,
          goldenFile = "$baseOutputPath$filePath",
          timestampNs = CFAbsoluteTimeGetCurrent().toLong(),
          contextData = emptyMap()
        )
        writeJson(result, resultsDir, nameWithoutExtension)
        throw AssertionError("Pixel changed for $filePath")
      }
      val result = CaptureResult.Unchanged(
        goldenFile = "$baseOutputPath$filePath",
        timestampNs = CFAbsoluteTimeGetCurrent().toLong(),
        contextData = emptyMap()
      )
      writeJson(result, resultsDir, nameWithoutExtension)
    }

    RoborazziTaskType.CompareAndRecord -> {
      val oldImage = loadOldImage(baseOutputPath, filePath)
      if (oldImage == null) {
        writeImage(newImage, goldenFilePath)
        writeImage(
          newImage = generateCompareImage(oldImage, newImage)?.let { UIImage(it) } ?: newImage,
          path = compareFilePath
        )
        val result = CaptureResult.Added(
          compareFile = goldenFilePath,
          actualFile = goldenFilePath,
          goldenFile = "$baseOutputPath$filePath",
          timestampNs = CFAbsoluteTimeGetCurrent().toLong(),
          contextData = emptyMap()
        )
        writeJson(result, resultsDir, nameWithoutExtension)
        return
      }
      if (hasChangedPixel(oldImage, newImage)) {
        writeImage(newImage, goldenFilePath)
        writeImage(
          newImage = generateCompareImage(oldImage, newImage)?.let { UIImage(it) } ?: newImage,
          path = compareFilePath
        )
        val result = CaptureResult.Changed(
          compareFile = goldenFilePath,
          actualFile = goldenFilePath,
          goldenFile = "$baseOutputPath$filePath",
          timestampNs = CFAbsoluteTimeGetCurrent().toLong(),
          contextData = emptyMap()
        )
        writeJson(result, resultsDir, nameWithoutExtension)
        return
      }
      val result = CaptureResult.Unchanged(
        goldenFile = "$baseOutputPath$filePath",
        timestampNs = CFAbsoluteTimeGetCurrent().toLong(),
        contextData = emptyMap()
      )
      writeJson(result, resultsDir, nameWithoutExtension)
    }
  }
}

private fun writeImage(newImage: UIImage, path: String) {
  UIImagePNGRepresentation(newImage)!!.writeToFile(
    path,
    true
  )
  println("Image is saved $path")
}

private fun loadOldImage(
  baseOutputPath: String,
  filePath: String
): UIImage? {
  if (!NSFileManager.defaultManager.fileExistsAtPath(baseOutputPath + filePath)) {
    return null
  }
  @Suppress("USELESS_CAST")
  val image: UIImage? = UIImage(baseOutputPath + filePath) as UIImage?
  if (image == null) {
    println("can't load old image from $baseOutputPath$filePath")
  }
  val oldImage = image?.let { convertImageFormat(it) }
  if (oldImage == null) {
    println("can't convert old image from $baseOutputPath$filePath")
  }
  return oldImage
}

private fun writeJson(
  result: CaptureResult,
  resultsDir: String,
  nameWithoutExtension: String
) {
  val module = SerializersModule {
    polymorphic(CaptureResult::class, CaptureResult.Added::class, CaptureResult.Added.serializer())
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
  val json = Json {
    serializersModule = module
  }
  json.encodeToJsonElement(PolymorphicSerializer(CaptureResult::class), result)
    .toString()
    .data()
    .writeToFile(
      path = getReportFileName(
        absolutePath = resultsDir,
        timestampNs = result.timestampNs,
        nameWithoutExtension = nameWithoutExtension
      ), atomically = true
    )

  println(
    "Report file is saved ${
      getReportFileName(
        absolutePath = resultsDir,
        timestampNs = result.timestampNs,
        nameWithoutExtension = nameWithoutExtension
      )
    }"
  )
}
