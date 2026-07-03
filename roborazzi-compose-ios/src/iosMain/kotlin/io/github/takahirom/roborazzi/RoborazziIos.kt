@file:OptIn(InternalRoborazziApi::class, ExperimentalRoborazziApi::class)

package io.github.takahirom.roborazzi

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.UIImageRoboCanvas
import com.github.takahirom.roborazzi.processOutputImageAndReport
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.github.takahirom.roborazzi.roborazziSystemPropertyProjectPath
import kotlin.math.roundToInt

/**
 * Resolves a golden [filePath] against the Roborazzi output directory and the
 * project path, keeping the resolution behavior of the previous iOS
 * implementation. Absolute paths are used as-is.
 *
 * The `_compare` / `_actual` files are NOT resolved here; the common
 * [processOutputImageAndReport] pipeline derives them from
 * [RoborazziOptions.CompareOptions.outputDirectoryPath].
 */
private fun resolveGoldenFilePath(filePath: String): String {
  if (filePath.startsWith("/")) return filePath
  val projectDir = roborazziSystemPropertyProjectPath()
  val outputDir = roborazziSystemPropertyOutputDirectory()
  val baseOutputPath = if (outputDir.startsWith("/")) outputDir else "$projectDir/$outputDir"
  return "$baseOutputPath/$filePath"
}

/**
 * Captures the node as an image and runs it through the shared Roborazzi
 * pipeline (record / compare / verify), the same pipeline used by the JVM and
 * Compose Desktop targets.
 *
 * On iOS [filePath] is required (there is no automatic file-name generation).
 */
@ExperimentalRoborazziApi
@OptIn(ExperimentalTestApi::class)
fun SemanticsNodeInteraction.captureRoboImage(
  composeUiTest: ComposeUiTest,
  filePath: String,
  roborazziOptions: RoborazziOptions = RoborazziOptions(),
) {
  if (!roborazziOptions.taskType.isEnabled()) {
    return
  }
  val pixelMap = captureToImage().toPixelMap()
  val width = pixelMap.width
  val height = pixelMap.height
  // Compose's PixelMap exposes straight (un-premultiplied) sRGB colors via get().
  // Flatten them into a straight R, G, B, A byte buffer, which
  // UIImageRoboCanvas.fromUnpremultipliedRgbaBytes premultiplies as it draws
  // into its canonical CoreGraphics context.
  val bytes = ByteArray(width * height * 4)
  var index = 0
  for (y in 0 until height) {
    for (x in 0 until width) {
      val color = pixelMap[x, y]
      bytes[index++] = (color.red * 255f).roundToInt().toByte()
      bytes[index++] = (color.green * 255f).roundToInt().toByte()
      bytes[index++] = (color.blue * 255f).roundToInt().toByte()
      bytes[index++] = (color.alpha * 255f).roundToInt().toByte()
    }
  }
  val newCanvas = UIImageRoboCanvas.fromUnpremultipliedRgbaBytes(width, height, bytes)
  try {
    processOutputImageAndReport(
      newRoboCanvas = newCanvas,
      goldenFilePath = resolveGoldenFilePath(filePath),
      contextData = roborazziOptions.contextData,
      roborazziOptions = roborazziOptions,
      emptyCanvasFactory = { w, h, _, _ ->
        UIImageRoboCanvas.create(w, h)
      },
      canvasFactoryFromFile = { path, _ ->
        // TODO: fromFile returns null when the golden PNG can't be decoded.
        //  The common CanvasFactoryFromPath contract is non-null, so we fail
        //  fast here rather than silently comparing against an empty canvas.
        UIImageRoboCanvas.fromFile(path)
          ?: error("Failed to load golden image from $path")
      },
      comparisonCanvasFactory = { goldenCanvas, actualCanvas, _, _ ->
        UIImageRoboCanvas.generateCompareCanvas(
          goldenCanvas = goldenCanvas as UIImageRoboCanvas,
          newCanvas = actualCanvas as UIImageRoboCanvas,
        )
      },
    )
  } finally {
    newCanvas.release()
  }
}
