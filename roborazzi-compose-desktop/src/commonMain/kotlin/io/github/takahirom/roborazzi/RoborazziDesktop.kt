package io.github.takahirom.roborazzi

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.AwtRoboCanvas
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.toRoboComponentTree
import com.github.takahirom.roborazzi.DefaultFileNameGenerator
import com.github.takahirom.roborazzi.RoboCanvas
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.fileWithRecordFilePathStrategy
import com.github.takahirom.roborazzi.processOutputImageAndReport
import com.github.takahirom.roborazzi.provideRoborazziContext
import java.awt.image.BufferedImage
import java.io.File

fun SemanticsNodeInteraction.captureRoboImage(
  filePath: String = DefaultFileNameGenerator.generateFilePath(),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
) {
  if (!roborazziOptions.taskType.isEnabled()) {
    return
  }
  captureRoboImage(
    file = fileWithRecordFilePathStrategy(filePath),
    roborazziOptions = roborazziOptions
  )
}

fun SemanticsNodeInteraction.captureRoboImage(
  file: File,
  roborazziOptions: RoborazziOptions
) {
  if (!roborazziOptions.taskType.isEnabled()) {
    return
  }
  val node = this.fetchSemanticsNode()
  val density = node.layoutInfo.density
  val uiTreeDump = writeUiTreeDumpIfEnabledDesktop(
    serializationTree = { node.toRoboComponentTree() },
    goldenFile = file,
    roborazziOptions = roborazziOptions,
  )
  val awtImage = this.captureToImage().toAwtImage()
  val canvas = AwtRoboCanvas(
    width = awtImage.width,
    height = awtImage.height,
    filled = true,
    bufferedImageType = BufferedImage.TYPE_INT_ARGB
  )
  canvas.apply {
    drawImage(awtImage)
  }
  var actualImageWritten = false
  try {
    processOutputImageAndReportWithDefaults(
      canvas = canvas,
      goldenFile = file,
      roborazziOptions = uiTreeDump.effectiveOptions,
      density = density,
    ) { actualImageWritten = it }
  } finally {
    // Draw the annotated image even when verification failed: the output image
    // (`_actual`) has already been written, so its annotation must still be
    // produced. writeAnnotatedImage never throws, so it cannot mask the original
    // assertion error propagating from the report step. The pipeline reports
    // whether it wrote the source image this run, so a stale `_actual` is never
    // reused.
    uiTreeDump.writeAnnotatedImage(actualImageWritten)
    canvas.release()
  }
}

fun ImageBitmap.captureRoboImage(
  filePath: String = DefaultFileNameGenerator.generateFilePath(),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
) {
  if (!roborazziOptions.taskType.isEnabled()) {
    return
  }
  captureRoboImage(
    file = fileWithRecordFilePathStrategy(filePath),
    roborazziOptions = roborazziOptions
  )
}

fun ImageBitmap.captureRoboImage(
  file: File,
  roborazziOptions: RoborazziOptions
) {
  if (!roborazziOptions.taskType.isEnabled()) {
    return
  }
  val awtImage = this.toAwtImage()
  val canvas = AwtRoboCanvas(
    width = awtImage.width,
    height = awtImage.height,
    filled = true,
    bufferedImageType = BufferedImage.TYPE_INT_ARGB
  )
  canvas.apply {
    drawImage(awtImage)
  }
  processOutputImageAndReportWithDefaults(
    canvas = canvas,
    goldenFile = file,
    roborazziOptions = roborazziOptions,
    density = null
  )
  canvas.release()
}

fun processOutputImageAndReportWithDefaults(
  canvas: RoboCanvas,
  goldenFile: File,
  roborazziOptions: RoborazziOptions,
  density: Density?,
) {
  processOutputImageAndReportWithDefaults(
    canvas = canvas,
    goldenFile = goldenFile,
    roborazziOptions = roborazziOptions,
    density = density,
    reportActualImageWritten = {},
  )
}

@InternalRoborazziApi
fun processOutputImageAndReportWithDefaults(
  canvas: RoboCanvas,
  goldenFile: File,
  roborazziOptions: RoborazziOptions,
  density: Density?,
  reportActualImageWritten: (Boolean) -> Unit,
) {
  processOutputImageAndReport(
    newRoboCanvas = canvas,
    goldenFile = goldenFile,
    roborazziOptions = roborazziOptions,
    reportActualImageWritten = reportActualImageWritten,
    emptyCanvasFactory = { width, height, filled, bufferedImageType ->
      AwtRoboCanvas(
        width = width,
        height = height,
        filled = filled,
        bufferedImageType = bufferedImageType
      )
    },
    canvasFactoryFromFile = { file, bufferedImageType ->
      AwtRoboCanvas.load(
        file = file,
        bufferedImageType = bufferedImageType,
        imageIoFormat = roborazziOptions.recordOptions.imageIoFormat
      )
    },
    comparisonCanvasFactory = { goldenCanvas, actualCanvas, resizeScale, bufferedImageType ->
      AwtRoboCanvas.generateCompareCanvas(
          AwtRoboCanvas.Companion.ComparisonCanvasParameters.create(
            goldenCanvas = goldenCanvas as AwtRoboCanvas,
            newCanvas = actualCanvas as AwtRoboCanvas,
            newCanvasResize = resizeScale,
            bufferedImageType = bufferedImageType,
            oneDpPx = density?.run { 1.dp.toPx() },
            comparisonComparisonStyle = roborazziOptions.compareOptions.comparisonStyle,
          )
      )
    }
  )
}