package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.ROBORAZZI_ANNOTATED_FILE_PATH_KEY
import com.github.takahirom.roborazzi.ROBORAZZI_UI_TREE_FILE_PATH_KEY
import com.github.takahirom.roborazzi.RoboComponentTree
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.UiTreeAnnotation
import com.github.takahirom.roborazzi.UiTreeCaptureInfo
import com.github.takahirom.roborazzi.assignUiTreeNumbers
import com.github.takahirom.roborazzi.computeUiTreeAnnotations
import com.github.takahirom.roborazzi.roborazziAnnotatedImageSuffix
import com.github.takahirom.roborazzi.roborazziDebugLog
import com.github.takahirom.roborazzi.roborazziErrorLog
import com.github.takahirom.roborazzi.roborazziUiTreeSidecarSuffix
import com.github.takahirom.roborazzi.toUiTreeJson
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * The outcome of preparing the UI tree dump for a Compose Desktop capture.
 *
 * [effectiveOptions] is the [RoborazziOptions] to use for the actual image write:
 * when the dump was written, a copy whose `contextData` records the sidecar (and,
 * when enabled, the annotated image) path; otherwise the options unchanged.
 *
 * [writeAnnotatedImage] must be invoked AFTER the output image has been written
 * to disk (it copies that image and draws the numbered boxes on top). It is a
 * no-op when the feature is disabled or annotation is opted out, and it never
 * throws in a way that would fail the capture.
 *
 * This mirrors the Android `roborazzi` module's `UiTreeDumpWriteResult`, but the
 * annotated image is drawn with pure java.awt because android.graphics is not on
 * the Compose Desktop runtime classpath.
 */
internal class DesktopUiTreeDumpWriteResult(
  val effectiveOptions: RoborazziOptions,
  private val annotatedImageWriter: ((sourceWrittenThisRun: Boolean) -> Unit)?,
) {
  fun writeAnnotatedImage(sourceWrittenThisRun: Boolean) {
    annotatedImageWriter?.invoke(sourceWrittenThisRun)
  }
}

/**
 * Writes the `.uitree.json` sidecar next to the image the current task writes,
 * when [RoborazziOptions.uiTreeDumpOptions] is enabled, and prepares the annotated
 * Set-of-Mark image (drawn later via [DesktopUiTreeDumpWriteResult.writeAnnotatedImage]).
 *
 * Naming and `_actual` basename semantics match the Android implementation.
 * Informational only: any I/O problem is logged and swallowed.
 */
@OptIn(ExperimentalRoborazziApi::class)
internal fun writeUiTreeDumpIfEnabledDesktop(
  serializationTree: () -> RoboComponentTree,
  goldenFile: File,
  roborazziOptions: RoborazziOptions,
): DesktopUiTreeDumpWriteResult {
  val dumpOptions = roborazziOptions.uiTreeDumpOptions
    ?: return DesktopUiTreeDumpWriteResult(roborazziOptions, annotatedImageWriter = null)
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
    val sidecarFile = uiTreeSidecarFileDesktop(goldenFile, roborazziOptions)
    sidecarFile.parentFile?.mkdirs()
    sidecarFile.writeText(json)
    roborazziDebugLog { "UI tree sidecar written: ${sidecarFile.absolutePath}" }

    var contextData = roborazziOptions.contextData +
      (ROBORAZZI_UI_TREE_FILE_PATH_KEY to sidecarFile.absolutePath)

    val annotatedImageWriter: ((Boolean) -> Unit)? = if (dumpOptions.annotateImage) {
      val annotatedFile = annotatedImageFileDesktop(goldenFile, roborazziOptions)
      contextData = contextData + (ROBORAZZI_ANNOTATED_FILE_PATH_KEY to annotatedFile.absolutePath)
      val annotations = computeUiTreeAnnotations(tree, numbers, captureInfo)
      val sourceImageFile = currentRunImageFileDesktop(goldenFile, roborazziOptions);
      { sourceWrittenThisRun: Boolean ->
        writeAnnotatedImageDesktop(
          sourceImageFile = sourceImageFile,
          // The capture pipeline reports whether it wrote the source image this
          // run; on an unchanged verify it did not, so a stale `_actual` is
          // ignored and the writer falls back to the golden.
          sourceWrittenThisRun = sourceWrittenThisRun,
          fallbackImageFile = goldenFile,
          annotatedFile = annotatedFile,
          annotations = annotations,
        )
      }
    } else {
      null
    }

    DesktopUiTreeDumpWriteResult(
      effectiveOptions = roborazziOptions.copy(contextData = contextData),
      annotatedImageWriter = annotatedImageWriter,
    )
  } catch (e: Exception) {
    roborazziErrorLog("Roborazzi failed to write the UI tree dump: ${e.message}")
    DesktopUiTreeDumpWriteResult(roborazziOptions, annotatedImageWriter = null)
  }
}

@OptIn(ExperimentalRoborazziApi::class)
private fun RoborazziOptions.isPureCompareOrVerify(): Boolean =
  (taskType.isVerifying() || taskType.isComparing()) && !taskType.isRecording()

@OptIn(ExperimentalRoborazziApi::class)
private fun uiTreeSidecarFileDesktop(goldenFile: File, roborazziOptions: RoborazziOptions): File {
  val baseName = goldenFile.nameWithoutExtension
  return if (roborazziOptions.isPureCompareOrVerify()) {
    File(
      roborazziOptions.compareOptions.outputDirectoryPath,
      baseName + "_actual" + roborazziUiTreeSidecarSuffix
    ).absoluteFile
  } else {
    File(goldenFile.parentFile, baseName + roborazziUiTreeSidecarSuffix).absoluteFile
  }
}

@OptIn(ExperimentalRoborazziApi::class)
private fun annotatedImageFileDesktop(goldenFile: File, roborazziOptions: RoborazziOptions): File {
  val baseName = goldenFile.nameWithoutExtension
  return if (roborazziOptions.isPureCompareOrVerify()) {
    File(
      roborazziOptions.compareOptions.outputDirectoryPath,
      baseName + "_actual" + roborazziAnnotatedImageSuffix
    ).absoluteFile
  } else {
    File(goldenFile.parentFile, baseName + roborazziAnnotatedImageSuffix).absoluteFile
  }
}

@OptIn(ExperimentalRoborazziApi::class)
private fun currentRunImageFileDesktop(goldenFile: File, roborazziOptions: RoborazziOptions): File {
  return if (roborazziOptions.isPureCompareOrVerify()) {
    File(
      roborazziOptions.compareOptions.outputDirectoryPath,
      goldenFile.nameWithoutExtension + "_actual." + goldenFile.extension
    ).absoluteFile
  } else {
    goldenFile
  }
}

// Same opaque palette as the Android annotated image (see roborazzi module's
// `colors`), cycled by node number so the two platforms look alike.
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

private const val LabelPadding = 4

/**
 * Draws the numbered Set-of-Mark boxes onto a COPY of the output screenshot and
 * writes it to [annotatedFile], using pure java.awt (android.graphics is not on
 * the Compose Desktop runtime classpath). Matches the Android look: an outlined
 * box plus a filled label with the node number and contrasting text, colored by
 * the shared palette cycled by number.
 *
 * Never throws in a way that would fail the capture; problems are logged.
 */
private fun writeAnnotatedImageDesktop(
  sourceImageFile: File,
  sourceWrittenThisRun: Boolean,
  fallbackImageFile: File,
  annotatedFile: File,
  annotations: List<UiTreeAnnotation>,
) {
  try {
    val source = when {
      sourceWrittenThisRun && sourceImageFile.exists() -> sourceImageFile
      fallbackImageFile.exists() -> fallbackImageFile
      else -> {
        roborazziDebugLog {
          "Annotated image skipped: no output image at ${sourceImageFile.absolutePath}"
        }
        return
      }
    }
    val original = ImageIO.read(source) ?: run {
      roborazziErrorLog("Roborazzi failed to load the output image at ${source.absolutePath}")
      return
    }
    val image = BufferedImage(original.width, original.height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
      graphics.drawImage(original, 0, 0, null)
      graphics.setRenderingHint(
        RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_ON,
      )
      graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, 16)
      val metrics = graphics.fontMetrics
      for (annotation in annotations) {
        val boxColor = annotationColors[annotation.number % annotationColors.size] or (0xFF shl 24)
        val awtColor = Color(boxColor, true)
        val bounds = annotation.bounds
        // Outline whose stroke width mirrors AwtRoboCanvas.drawRectOutline.
        val strokeWidth = minOf(bounds.width, bounds.height).toFloat() / 20f
        graphics.color = awtColor
        graphics.stroke = BasicStroke(maxOf(1f, strokeWidth))
        graphics.drawRect(bounds.left, bounds.top, bounds.width, bounds.height)

        val label = annotation.number.toString()
        val textWidth = metrics.stringWidth(label)
        val textHeight = metrics.ascent + metrics.descent
        val labelWidth = textWidth + LabelPadding * 2
        val labelHeight = textHeight + LabelPadding * 2
        val labelLeft = bounds.left.coerceIn(0, maxOf(0, image.width - labelWidth))
        val labelTop = bounds.top.coerceIn(0, maxOf(0, image.height - labelHeight))
        graphics.color = awtColor
        graphics.fillRect(labelLeft, labelTop, labelWidth, labelHeight)
        graphics.color = if (isColorBright(boxColor)) Color.BLACK else Color.WHITE
        graphics.drawString(
          label,
          labelLeft + LabelPadding,
          labelTop + LabelPadding + metrics.ascent,
        )
      }
    } finally {
      graphics.dispose()
    }
    annotatedFile.parentFile?.mkdirs()
    ImageIO.write(image, "png", annotatedFile)
    roborazziDebugLog { "Annotated image written: ${annotatedFile.absolutePath}" }
  } catch (e: Exception) {
    roborazziErrorLog("Roborazzi failed to write the annotated image: ${e.message}")
  }
}
