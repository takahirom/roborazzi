package io.github.takahirom.roborazzi

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.github.takahirom.roborazzi.*
import java.awt.image.BufferedImage
import java.io.File

fun captureRoboImage(
  filePath: String = DefaultFileNameGenerator.generateFilePath("png"),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  content: @Composable () -> Unit,
) {
  captureRoboImage(
    file = File(filePath),
    content = content
  )
}

@OptIn(ExperimentalTestApi::class)
fun captureRoboImage(
  file: File,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  content: @Composable () -> Unit,
) = runDesktopComposeUiTest() {
  setContent(content)
  val image = captureToImage().toAwtImage()
  val canvas = AwtRoboCanvas(
    width = image.width,
    height = image.height,
    filled = true,
    bufferedImageType = BufferedImage.TYPE_INT_ARGB
  )
  canvas.apply {
    drawImage(image)
  }
  processOutputImageAndReportWithDefaults(
    canvas = canvas,
    goldenFile = file,
    roborazziOptions = roborazziOptions
  )
  canvas.release()
}

fun processOutputImageAndReportWithDefaults(
  canvas: RoboCanvas,
  goldenFile: File,
  roborazziOptions: RoborazziOptions,
) {
  processOutputImageAndReport(
    canvas = canvas,
    goldenFile = goldenFile,
    roborazziOptions = roborazziOptions,
    canvasFactory = { width, height, filled, bufferedImageType ->
      AwtRoboCanvas(
        width = width,
        height = height,
        filled = filled,
        bufferedImageType = bufferedImageType
      )
    },
    canvasFromFile = { file, bufferedImageType ->
      AwtRoboCanvas.load(file, bufferedImageType)
    },
    generateCompareCanvas = { actualCanvas, resizeScale, bufferedImageType ->
      AwtRoboCanvas.generateCompareCanvas(
        this as AwtRoboCanvas,
        actualCanvas as AwtRoboCanvas,
        resizeScale,
        bufferedImageType
      )
    }
  )
}