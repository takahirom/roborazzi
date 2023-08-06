package io.github.takahirom.roborazzi

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.github.takahirom.roborazzi.RoboCanvas
import java.awt.image.BufferedImage
import java.io.File

fun captureRoboImage(
  filePath: String,
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
  content: @Composable () -> Unit,
) = runDesktopComposeUiTest() {
  setContent(content)
  val image = captureToImage().toAwtImage()
  RoboCanvas(
    width = image.width,
    height = image.height,
    filled = true,
    bufferedImageType =  BufferedImage.TYPE_INT_ARGB
  ).apply {
    drawImage(image)
  }
    .save(file, 1.0)
}
