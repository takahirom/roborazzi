@file:OptIn(
  InternalRoborazziApi::class,
  ExperimentalRoborazziApi::class,
  ExperimentalForeignApi::class,
)

package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.ROBORAZZI_ANNOTATED_FILE_PATH_KEY
import com.github.takahirom.roborazzi.ROBORAZZI_UI_TREE_FILE_PATH_KEY
import com.github.takahirom.roborazzi.RoboComponentTree
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.UiTreeAnnotation
import com.github.takahirom.roborazzi.UiTreeCaptureInfo
import com.github.takahirom.roborazzi.assignUiTreeNumbers
import com.github.takahirom.roborazzi.computeUiTreeAnnotations
import com.github.takahirom.roborazzi.roborazziAnnotatedImageSuffix
import com.github.takahirom.roborazzi.roborazziReportLog
import com.github.takahirom.roborazzi.roborazziSystemPropertyProjectPath
import com.github.takahirom.roborazzi.roborazziUiTreeSidecarSuffix
import com.github.takahirom.roborazzi.toUiTreeJson
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.writeToFile
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIRectFill
import platform.UIKit.drawAtPoint
import platform.UIKit.drawInRect
import platform.UIKit.sizeWithAttributes
import kotlin.math.max
import kotlin.math.min

/**
 * The outcome of preparing the UI tree dump for a Compose iOS capture.
 *
 * [effectiveOptions] is the [RoborazziOptions] to use for the actual image write:
 * when the dump was written, a copy whose `contextData` records the sidecar (and,
 * when enabled, the annotated image) path; otherwise the options unchanged.
 *
 * [writeAnnotatedImage] must be invoked AFTER the output image has been written to
 * disk (it loads that PNG and draws the numbered boxes on top). It is a no-op when
 * the feature is disabled or annotation is opted out, and it never throws in a way
 * that would fail the capture.
 *
 * Mirrors the Android / Desktop write results; the annotated image is drawn with
 * UIKit/CoreGraphics because there is no AWT on iOS.
 */
internal class IosUiTreeDumpWriteResult(
  val effectiveOptions: RoborazziOptions,
  private val annotatedImageWriter: ((sourceWrittenThisRun: Boolean) -> Unit)?,
) {
  fun writeAnnotatedImage(sourceWrittenThisRun: Boolean) {
    annotatedImageWriter?.invoke(sourceWrittenThisRun)
  }
}

/**
 * Writes the `.uitree.json` sidecar (and, when [UiTreeDumpOptions.annotateImage]
 * is true, prepares the annotated Set-of-Mark PNG) for a Compose iOS capture, when
 * [RoborazziOptions.uiTreeDumpOptions] is enabled.
 *
 * Naming and `_actual` basename semantics mirror the Android / Desktop
 * implementations. Informational only: any problem is logged and swallowed so it
 * never fails the capture.
 */
internal fun writeUiTreeDumpIfEnabledIos(
  serializationTree: () -> RoboComponentTree,
  resolvedGoldenFilePath: String,
  roborazziOptions: RoborazziOptions,
): IosUiTreeDumpWriteResult {
  val dumpOptions = roborazziOptions.uiTreeDumpOptions
    ?: return IosUiTreeDumpWriteResult(roborazziOptions, annotatedImageWriter = null)
  return try {
    val tree = serializationTree()
    val scale = roborazziOptions.recordOptions.resizeScale
    val captureInfo = UiTreeCaptureInfo(
      imageWidth = (tree.width * scale).toInt(),
      imageHeight = (tree.height * scale).toInt(),
      scale = scale,
    )
    val numbers = assignUiTreeNumbers(tree, dumpOptions.isAnnotatable)
    val json = tree.toUiTreeJson(captureInfo = captureInfo, numbers = numbers)

    val sidecarPath = uiTreeSidecarPathIos(resolvedGoldenFilePath, roborazziOptions)
    writeTextFileIos(sidecarPath, json)
    roborazziReportLog("UI tree sidecar written: $sidecarPath")

    var contextData = roborazziOptions.contextData +
      (ROBORAZZI_UI_TREE_FILE_PATH_KEY to sidecarPath)

    val annotatedImageWriter: ((Boolean) -> Unit)? = if (dumpOptions.annotateImage) {
      val annotatedPath = annotatedImagePathIos(resolvedGoldenFilePath, roborazziOptions)
      contextData = contextData + (ROBORAZZI_ANNOTATED_FILE_PATH_KEY to annotatedPath)
      val annotations = computeUiTreeAnnotations(tree, numbers, captureInfo)
      val sourceImagePath = currentRunImagePathIos(resolvedGoldenFilePath, roborazziOptions);
      { sourceWrittenThisRun: Boolean ->
        writeAnnotatedImageIos(
          sourceImagePath = sourceImagePath,
          // The capture pipeline reports whether it wrote the source image this
          // run; on an unchanged verify it did not, so a stale `_actual` is
          // ignored and the writer falls back to the golden.
          sourceWrittenThisRun = sourceWrittenThisRun,
          fallbackImagePath = resolvedGoldenFilePath,
          annotatedPath = annotatedPath,
          annotations = annotations,
        )
      }
    } else {
      null
    }

    IosUiTreeDumpWriteResult(
      effectiveOptions = roborazziOptions.copy(contextData = contextData),
      annotatedImageWriter = annotatedImageWriter,
    )
  } catch (e: Exception) {
    // The dump is informational only; never fail the capture because of it.
    roborazziReportLog("Roborazzi failed to write the UI tree dump: ${e.message}")
    IosUiTreeDumpWriteResult(roborazziOptions, annotatedImageWriter = null)
  }
}

private fun RoborazziOptions.isPureCompareOrVerify(): Boolean =
  (taskType.isVerifying() || taskType.isComparing()) && !taskType.isRecording()

/**
 * Resolves a (possibly project-relative) [path] to an absolute path, mirroring the
 * common image pipeline's `roborazziToAbsolutePath` on iOS (see
 * `resolveRoborazziAbsolutePath` in roborazzi-core): the Gradle plugin passes the
 * compare output directory as a project-relative path together with an absolute
 * `roborazzi.project.path`, and an iOS simulator's working directory is not the
 * project, so relative paths must be resolved against the project path (falling
 * back to the process working directory when no project path is configured).
 *
 * Without this the sidecar / annotated / `_actual` source paths would land in a
 * different directory than the image the shared pipeline writes.
 */
private fun resolveRoborazziPathIos(path: String): String {
  if (path.startsWith("/")) return path
  val projectPath = roborazziSystemPropertyProjectPath()
  val base = if (projectPath == ".") {
    NSFileManager.defaultManager.currentDirectoryPath
  } else {
    projectPath
  }
  return "$base/$path"
}

private fun uiTreeSidecarPathIos(
  resolvedGoldenFilePath: String,
  roborazziOptions: RoborazziOptions,
): String = sidecarStylePathIos(resolvedGoldenFilePath, roborazziOptions, roborazziUiTreeSidecarSuffix)

private fun annotatedImagePathIos(
  resolvedGoldenFilePath: String,
  roborazziOptions: RoborazziOptions,
): String = sidecarStylePathIos(resolvedGoldenFilePath, roborazziOptions, roborazziAnnotatedImageSuffix)

/**
 * Resolves a sidecar-style path (`baseName + suffix`) from the resolved golden
 * image path, mirroring where the shared pipeline writes the image for the current
 * task: next to the golden on record, and the `_actual` basename in the compare
 * output directory on pure compare/verify.
 */
private fun sidecarStylePathIos(
  resolvedGoldenFilePath: String,
  roborazziOptions: RoborazziOptions,
  suffix: String,
): String {
  val fileName = resolvedGoldenFilePath.substringAfterLast("/")
  val baseName = fileName.substringBeforeLast(".")
  return if (roborazziOptions.isPureCompareOrVerify()) {
    val outputDir = roborazziOptions.compareOptions.outputDirectoryPath.trimEnd('/')
    resolveRoborazziPathIos("$outputDir/${baseName}_actual$suffix")
  } else {
    val parentPath = resolvedGoldenFilePath.substringBeforeLast("/")
    "$parentPath/$baseName$suffix"
  }
}

/**
 * The image file the current task writes and that the annotated image copies: the
 * golden path on record, and the `_actual` image in the compare output directory
 * on pure compare/verify. On an unchanged verify no `_actual` image is written, so
 * the caller falls back to the (identical) golden.
 */
private fun currentRunImagePathIos(
  resolvedGoldenFilePath: String,
  roborazziOptions: RoborazziOptions,
): String {
  if (!roborazziOptions.isPureCompareOrVerify()) return resolvedGoldenFilePath
  val fileName = resolvedGoldenFilePath.substringAfterLast("/")
  val baseName = fileName.substringBeforeLast(".")
  val extension = fileName.substringAfterLast(".", "png")
  val outputDir = roborazziOptions.compareOptions.outputDirectoryPath.trimEnd('/')
  return resolveRoborazziPathIos("$outputDir/${baseName}_actual.$extension")
}

private fun writeTextFileIos(path: String, text: String) {
  val parentPath = path.substringBeforeLast("/")
  if (parentPath.isNotEmpty() && parentPath != path) {
    NSFileManager.defaultManager.createDirectoryAtPath(
      parentPath,
      withIntermediateDirectories = true,
      attributes = null,
      error = null,
    )
  }
  val written = (text as NSString).writeToFile(
    path = path,
    atomically = true,
    encoding = NSUTF8StringEncoding,
    error = null,
  )
  if (!written) {
    roborazziReportLog("Roborazzi failed to write $path")
  }
}

// Same opaque palette as the Android / Desktop annotated image, cycled by node
// number so all platforms look alike.
private val annotationColors = listOf(
  0x3F9101,
  0x0E4A8E,
  0xBCBF01,
  0xBC0BA2,
  0x61AA0D,
  0x3D017A,
  0xD6A60A,
  0x7710A3,
  0xA502CE,
  0xeb5a00,
)

private fun isColorBright(colorArgb: Int): Boolean {
  val alpha = (colorArgb ushr 24) and 0xFF
  val red = (colorArgb ushr 16) and 0xFF
  val green = (colorArgb ushr 8) and 0xFF
  val blue = colorArgb and 0xFF
  val luminance = ((0.299 * red + 0.587 * green + 0.114 * blue) / 255) * alpha / 255
  return luminance > 0.5
}

private const val LabelPadding = 4.0
private const val LabelFontSize = 16.0

/**
 * Draws the numbered Set-of-Mark boxes onto a COPY of the output screenshot and
 * writes it to [annotatedPath], using UIKit/CoreGraphics via
 * [UIGraphicsImageRenderer]. Matches the Android / Desktop look: an outlined box
 * plus a filled label carrying the node number with contrasting text, colored by
 * the shared palette cycled by number.
 *
 * Never throws in a way that would fail the capture; problems are logged.
 */
private fun writeAnnotatedImageIos(
  sourceImagePath: String,
  sourceWrittenThisRun: Boolean,
  fallbackImagePath: String,
  annotatedPath: String,
  annotations: List<UiTreeAnnotation>,
) {
  try {
    val fileManager = NSFileManager.defaultManager
    val source = when {
      sourceWrittenThisRun && fileManager.fileExistsAtPath(sourceImagePath) -> sourceImagePath
      fileManager.fileExistsAtPath(fallbackImagePath) -> fallbackImagePath
      else -> {
        roborazziReportLog("Annotated image skipped: no output image at $sourceImagePath")
        return
      }
    }
    val uiImage = UIImage(contentsOfFile = source)
    val cgImage = uiImage.CGImage ?: run {
      roborazziReportLog("Roborazzi failed to load the output image at $source")
      return
    }
    // Pixel dimensions of the written PNG define the annotation coordinate space.
    val pixelWidth = CGImageGetWidth(cgImage).toInt()
    val pixelHeight = CGImageGetHeight(cgImage).toInt()

    // scale=1 so output pixels equal the screenshot's pixels (not point-scaled).
    val format = UIGraphicsImageRendererFormat.defaultFormat()
    format.scale = 1.0
    val renderer = UIGraphicsImageRenderer(
      size = CGSizeMake(pixelWidth.toDouble(), pixelHeight.toDouble()),
      format = format,
    )
    val annotatedImage = renderer.imageWithActions {
      uiImage.drawInRect(CGRectMake(0.0, 0.0, pixelWidth.toDouble(), pixelHeight.toDouble()))
      for (annotation in annotations) {
        val boxColorArgb = annotationColors[annotation.number % annotationColors.size] or (0xFF shl 24)
        val uiColor = uiColorOf(boxColorArgb)
        val b = annotation.bounds
        val boxWidth = b.width.toDouble()
        val boxHeight = b.height.toDouble()

        // Outline whose stroke width mirrors the Android / Desktop rule.
        val strokeWidth = max(1.0, min(boxWidth, boxHeight) / 20.0)
        uiColor.setStroke()
        val outline = UIBezierPath.bezierPathWithRect(
          CGRectMake(b.left.toDouble(), b.top.toDouble(), boxWidth, boxHeight)
        )
        outline.lineWidth = strokeWidth
        outline.stroke()

        // Filled numbered label at the box's top-left, clamped to the image.
        val label = annotation.number.toString()
        val nsLabel = label as NSString
        val font = UIFont.boldSystemFontOfSize(LabelFontSize)
        val textColorArgb = if (isColorBright(boxColorArgb)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        val textAttributes = mapOf<Any?, Any>(
          NSFontAttributeName to font,
          NSForegroundColorAttributeName to uiColorOf(textColorArgb),
        )
        val (textWidth, textHeight) = nsLabel.sizeWithAttributes(
          mapOf<Any?, Any>(NSFontAttributeName to font)
        ).useContents { width to height }
        val labelWidth = textWidth + LabelPadding * 2
        val labelHeight = textHeight + LabelPadding * 2
        val labelLeft = b.left.toDouble().coerceIn(0.0, max(0.0, pixelWidth - labelWidth))
        val labelTop = b.top.toDouble().coerceIn(0.0, max(0.0, pixelHeight - labelHeight))
        uiColor.setFill()
        UIRectFill(CGRectMake(labelLeft, labelTop, labelWidth, labelHeight))
        nsLabel.drawAtPoint(
          CGPointMake(labelLeft + LabelPadding, labelTop + LabelPadding),
          textAttributes,
        )
      }
    }

    val png = UIImagePNGRepresentation(annotatedImage) ?: run {
      roborazziReportLog("Roborazzi failed to encode the annotated PNG for $annotatedPath")
      return
    }
    val parentPath = annotatedPath.substringBeforeLast("/")
    if (parentPath.isNotEmpty() && parentPath != annotatedPath) {
      fileManager.createDirectoryAtPath(
        parentPath,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
      )
    }
    if (png.writeToFile(annotatedPath, true)) {
      roborazziReportLog("Annotated image written: $annotatedPath")
    } else {
      roborazziReportLog("Roborazzi failed to write the annotated image to $annotatedPath")
    }
  } catch (e: Exception) {
    // The annotated image is informational only; never fail the capture.
    roborazziReportLog("Roborazzi failed to write the annotated image: ${e.message}")
  }
}

private fun uiColorOf(colorArgb: Int): UIColor {
  val alpha = ((colorArgb ushr 24) and 0xFF) / 255.0
  val red = ((colorArgb ushr 16) and 0xFF) / 255.0
  val green = ((colorArgb ushr 8) and 0xFF) / 255.0
  val blue = (colorArgb and 0xFF) / 255.0
  return UIColor.colorWithRed(red = red, green = green, blue = blue, alpha = alpha)
}
