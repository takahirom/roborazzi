package com.github.takahirom.roborazzi

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.TextPaint
import java.io.File

/**
 * Draws the numbered Set-of-Mark boxes onto a COPY of the output screenshot and
 * writes it to [annotatedFile].
 *
 * The source is [sourceImageFile] (the image the current task wrote), falling
 * back to [fallbackImageFile] (the identical golden image) when the current task
 * did not write a separate image, e.g. an unchanged verify. The boxes are drawn
 * on the FINAL output image (already resize-scaled), never on the golden used for
 * comparison, so this is purely a display artifact of the current run.
 *
 * This never throws in a way that would fail the capture; any problem is logged
 * and swallowed.
 */
@OptIn(ExperimentalRoborazziApi::class)
internal fun writeAnnotatedImage(
  sourceImageFile: File,
  fallbackImageFile: File,
  annotatedFile: File,
  annotations: List<UiTreeAnnotation>,
  roborazziOptions: RoborazziOptions,
) {
  try {
    val source = when {
      sourceImageFile.exists() -> sourceImageFile
      fallbackImageFile.exists() -> fallbackImageFile
      else -> {
        roborazziDebugLog {
          "Annotated image skipped: no output image at ${sourceImageFile.absolutePath}"
        }
        return
      }
    }
    val recordOptions = roborazziOptions.recordOptions
    val bufferedImageType = recordOptions.pixelBitConfig.toBufferedImageType()
    val canvas = AwtRoboCanvas.load(
      file = source,
      bufferedImageType = bufferedImageType,
      imageIoFormat = recordOptions.imageIoFormat,
    )
    try {
      drawAnnotations(canvas, annotations)
      annotatedFile.parentFile?.mkdirs()
      canvas.save(
        path = annotatedFile.absolutePath,
        // The source image is already resize-scaled; do not scale it again.
        resizeScale = 1.0,
        contextData = emptyMap(),
        imageIoFormat = recordOptions.imageIoFormat,
      )
      roborazziDebugLog { "Annotated image written: ${annotatedFile.absolutePath}" }
    } finally {
      canvas.release()
    }
  } catch (e: Exception) {
    // The annotated image is informational only; never fail the capture.
    roborazziErrorLog("Roborazzi failed to write the annotated image: ${e.message}")
  }
}

private const val LabelPadding = 4

/**
 * Draws each annotation as a rectangle outline plus a small filled label showing
 * its number near the box's top-left corner. Colors cycle by number, reusing the
 * palette and helpers from the Dump-mode drawing.
 */
private fun drawAnnotations(canvas: AwtRoboCanvas, annotations: List<UiTreeAnnotation>) {
  val outlinePaint = Paint()
  val fillPaint = Paint().apply { style = Paint.Style.FILL }
  val textPaint = TextPaint().apply {
    isAntiAlias = true
    textSize = 16F
  }
  for (annotation in annotations) {
    // Opaque color cycled by number (same palette as Dump mode).
    val boxColor = colors[annotation.number % colors.size] + AwtRoboCanvas.TRANSPARENT_NONE
    val bounds = annotation.bounds
    canvas.drawRectOutline(
      Rect(bounds.left, bounds.top, bounds.right, bounds.bottom),
      outlinePaint.apply { color = boxColor },
    )

    val texts = listOf(annotation.number.toString())
    val (textWidth, textHeight) = canvas.textCalc(texts)
    val labelWidth = textWidth + LabelPadding * 2
    val labelHeight = textHeight + LabelPadding * 2
    // Keep the label inside the image even for boxes hugging the edges.
    val labelLeft = bounds.left.coerceIn(0, maxOf(0, canvas.width - labelWidth))
    val labelTop = bounds.top.coerceIn(0, maxOf(0, canvas.height - labelHeight))
    canvas.drawRect(
      Rect(labelLeft, labelTop, labelLeft + labelWidth, labelTop + labelHeight),
      fillPaint.apply { color = boxColor },
    )
    canvas.drawText(
      labelLeft.toFloat() + LabelPadding,
      labelTop.toFloat() + LabelPadding + textHeight,
      texts,
      textPaint.apply {
        // Contrasting text on the filled label.
        color = if (isColorBright(boxColor)) Color.BLACK else Color.WHITE
      },
    )
  }
}
