@file:OptIn(InternalRoborazziApi::class, ExperimentalRoborazziApi::class)

package io.github.takahirom.roborazzi

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.UIImageRoboCanvas
import com.github.takahirom.roborazzi.applyContextDataPolicy
import com.github.takahirom.roborazzi.processOutputImageAndReport
import com.github.takahirom.roborazzi.roborazziReportLog
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.github.takahirom.roborazzi.roborazziSystemPropertyProjectPath
import com.github.takahirom.roborazzi.toRoboComponentTree
import kotlin.math.roundToInt

/**
 * Resolves a golden [filePath] against the Roborazzi output directory and the
 * project path, keeping the resolution behavior of the previous iOS
 * implementation. Absolute paths are used as-is.
 *
 * The `_compare` / `_actual` files are NOT resolved here; the common
 * [processOutputImageAndReport] pipeline derives them from
 * [RoborazziOptions.CompareOptions.outputDirectoryPath].
 *
 * Note on RoborazziRecordFilePathStrategy: on iOS the [filePath] is always
 * required (there is no test-name based auto-generation), and a simulator test
 * has no project-relative current working directory the way a JVM/Gradle test
 * run does. The two JVM strategies therefore collapse to a single sensible iOS
 * behavior: a relative path is resolved against the Roborazzi output directory
 * (equivalent to RelativePathFromRoborazziContextOutputDirectory). The strategy
 * property is intentionally not consulted here.
 */
private fun resolveGoldenFilePath(filePath: String): String {
  if (filePath.startsWith("/")) return filePath
  val projectDir = roborazziSystemPropertyProjectPath()
  val outputDir = roborazziSystemPropertyOutputDirectory()
  val baseOutputPath = if (outputDir.startsWith("/")) outputDir else "$projectDir/$outputDir"
  return "$baseOutputPath/$filePath"
}

private var warnedUnsupportedPixelBitConfig = false

/**
 * iOS bitmap contexts only support 8-bit RGBA. CoreGraphics has no 5-6-5
 * ([RoborazziOptions.PixelBitConfig.Rgb565]) bitmap format (the closest,
 * 16bpp 5-5-5 with a skipped alpha bit, is a different layout), so the request
 * is honored as Argb8888 instead of being silently ignored. Warns once.
 */
private fun warnIfUnsupportedPixelBitConfig(pixelBitConfig: RoborazziOptions.PixelBitConfig) {
  if (pixelBitConfig == RoborazziOptions.PixelBitConfig.Rgb565 && !warnedUnsupportedPixelBitConfig) {
    warnedUnsupportedPixelBitConfig = true
    roborazziReportLog(
      "Roborazzi Warning: PixelBitConfig.Rgb565 is not supported on iOS " +
        "(CoreGraphics bitmap contexts have no 5-6-5 format); falling back to Argb8888."
    )
  }
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
  warnIfUnsupportedPixelBitConfig(roborazziOptions.recordOptions.pixelBitConfig)
  // Density drives the 16dp/4dp grid spacing and label font size for
  // ComparisonStyle.Grid, mirroring the JVM/Compose Desktop pipeline.
  val node = fetchSemanticsNode()
  val oneDpPx = with(node.layoutInfo.density) { 1.dp.toPx() }
  val resolvedGoldenFilePath = resolveGoldenFilePath(filePath)
  // Writes the .uitree.json sidecar and prepares the annotated Set-of-Mark image
  // (drawn after the screenshot is written, via writeAnnotatedImage()).
  val uiTreeDump = writeUiTreeDumpIfEnabledIos(
    serializationTree = { node.toRoboComponentTree() },
    resolvedGoldenFilePath = resolvedGoldenFilePath,
    roborazziOptions = roborazziOptions,
  )
  val effectiveOptions = uiTreeDump.effectiveOptions
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
  var actualImageWritten = false
  try {
    try {
      processOutputImageAndReport(
      newRoboCanvas = newCanvas,
      goldenFilePath = resolvedGoldenFilePath,
      reportActualImageWritten = { actualImageWritten = it },
      // Honor the roborazzi.contextdata flag like the JVM facade does. The JVM
      // facade additionally injects the test class name; that default is JVM-only
      // (there is no provideRoborazziContext on iOS), so iOS records only the
      // user-supplied contextData. effectiveOptions carries the UI tree sidecar
      // path in contextData when the dump feature is enabled.
      contextData = applyContextDataPolicy(effectiveOptions),
      roborazziOptions = effectiveOptions,
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
      comparisonCanvasFactory = { goldenCanvas, actualCanvas, resizeScale, _ ->
        val grid = roborazziOptions.compareOptions.comparisonStyle
          as? RoborazziOptions.CompareOptions.ComparisonStyle.Grid
        // Pass the tier spacings through unchanged: a null spacing means the
        // caller disabled that grid tier (matching AwtRoboCanvas, which skips a
        // tier whose spacing is null), so it must not be replaced with a default.
        UIImageRoboCanvas.generateCompareCanvas(
          goldenCanvas = goldenCanvas as UIImageRoboCanvas,
          newCanvas = actualCanvas as UIImageRoboCanvas,
          // The actual canvas is full size; scale it to match the golden that
          // was saved at resizeScale so the compare sections stay aligned.
          newCanvasResize = resizeScale,
          useGrid = grid != null,
          oneDpPx = oneDpPx,
          bigLineSpaceDp = grid?.bigLineSpaceDp,
          smallLineSpaceDp = grid?.smallLineSpaceDp,
          hasLabel = grid?.hasLabel ?: true,
        )
      },
      )
    } finally {
      // The screenshot (golden or _actual) has now been written; draw the
      // annotated Set-of-Mark image on top of it, even when verification failed
      // (the `_actual` is already written). No-op when the feature is off or
      // opted out, and it never throws, so it cannot mask the assertion error.
      // The pipeline reports whether it wrote the source image this run, so a
      // stale `_actual` is never reused.
      uiTreeDump.writeAnnotatedImage(actualImageWritten)
    }
  } finally {
    newCanvas.release()
  }
}
