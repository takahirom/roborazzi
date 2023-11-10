package io.github.takahirom.roborazzi

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.*
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import java.io.File
import java.util.*

context(DesktopComposeUiTest)
@OptIn(ExperimentalTestApi::class)
fun SemanticsNodeInteraction.captureRoboImage(
  filePath: String = DefaultFileNameGenerator.generateFilePath("png"),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
) {
  if (!roborazziEnabled()) {
    return
  }
  captureRoboImage(
    file = fileWithRecordFilePathStrategy(filePath),
    roborazziOptions = roborazziOptions
  )
}

context(DesktopComposeUiTest)
@OptIn(ExperimentalTestApi::class)
fun SemanticsNodeInteraction.captureRoboImage(
  file: File,
  roborazziOptions: RoborazziOptions
) {
  if (!roborazziEnabled()) {
    return
  }
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
  processOutputImageAndReportWithDefaults(
    canvas = canvas,
    goldenFile = file,
    roborazziOptions = roborazziOptions
  )
  canvas.release()
}

fun ImageBitmap.captureRoboImage(
  filePath: String = DefaultFileNameGenerator.generateFilePath("png"),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
) {
  if (!roborazziEnabled()) {
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
  if (!roborazziEnabled()) {
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
    newRoboCanvas = canvas,
    goldenFile = goldenFile,
    roborazziOptions = roborazziOptions,
    emptyCanvasFactory = { width, height, filled, bufferedImageType ->
      AwtRoboCanvas(
        width = width,
        height = height,
        filled = filled,
        bufferedImageType = bufferedImageType
      )
    },
    canvasFactoryFromFile = { file, bufferedImageType ->
      AwtRoboCanvas.load(file, bufferedImageType)
    },
    comparisonCanvasFactory = { goldenCanvas, actualCanvas, resizeScale, bufferedImageType ->
      AwtRoboCanvas.generateCompareCanvas(
        goldenCanvas = goldenCanvas as AwtRoboCanvas,
        newCanvas = actualCanvas as AwtRoboCanvas,
        newCanvasResize = resizeScale,
        bufferedImageType = bufferedImageType,
        oneDpPx = GlobalDensity.run { 1.dp.toPx() }
      )
    }
  )
}

/**
 * From compose-multiplatform's density.kt
 */
private val GlobalDensity: Density
  get() = GraphicsEnvironment.getLocalGraphicsEnvironment()
    .defaultScreenDevice
    .defaultConfiguration
    .density

private val GraphicsConfiguration.density: Density
  get() = Density(
    defaultTransform.scaleX.toFloat(),
    fontScale = 1f
  )

