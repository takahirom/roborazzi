package com.github.takahirom.roborazzi

import com.dropbox.differ.ImageComparator
import java.io.File

fun processOutputImageAndReport(
  canvas: RoboCanvas,
  goldenFile: File,
  roborazziOptions: RoborazziOptions,
  canvasFactory: (Int, Int, Boolean, Int) -> RoboCanvas,
  canvasFromFile: (File, Int) -> RoboCanvas,
  generateCompareCanvas: RoboCanvas.(RoboCanvas, Double, Int) -> RoboCanvas,
) {
  val forbiddenFileSuffixes = listOf("_compare", "_actual")
  forbiddenFileSuffixes.forEach {
    if (goldenFile.nameWithoutExtension.endsWith(it)) {
      throw IllegalArgumentException("The file name should not end with $it because it is reserved for Roborazzi")
    }
  }
  val recordOptions = roborazziOptions.recordOptions
  val resizeScale = recordOptions.resizeScale
  if (roborazziCompareEnabled() || roborazziVerifyEnabled()) {
    val width = (canvas.croppedWidth * resizeScale).toInt()
    val height = (canvas.croppedHeight * resizeScale).toInt()
    val goldenRoboCanvas = if (goldenFile.exists()) {
      canvasFromFile(goldenFile, recordOptions.pixelBitConfig.toBufferedImageType())
    } else {
      canvasFactory(
        width,
        height,
        true,
        recordOptions.pixelBitConfig.toBufferedImageType()
      )
    }
    val changed = if (height == goldenRoboCanvas.height && width == goldenRoboCanvas.width) {
      val comparisonResult: ImageComparator.ComparisonResult =
        canvas.differ(goldenRoboCanvas, resizeScale)
      val changed = !roborazziOptions.compareOptions.resultValidator(comparisonResult)
      log("${goldenFile.name} The differ result :$comparisonResult changed:$changed")
      changed
    } else {
      log("${goldenFile.name} The image size is changed. actual = (${goldenRoboCanvas.width}, ${goldenRoboCanvas.height}), golden = (${canvas.croppedWidth}, ${canvas.croppedHeight})")
      true
    }

    val result: CompareReportCaptureResult = if (changed) {
      val compareFile = File(
        roborazziOptions.compareOptions.outputDirectoryPath,
        goldenFile.nameWithoutExtension + "_compare." + goldenFile.extension
      )
      val compareCanvas = goldenRoboCanvas.generateCompareCanvas(
        canvas,
        resizeScale,
        recordOptions.pixelBitConfig.toBufferedImageType()
      )
      compareCanvas
        .save(
          file = compareFile,
          resizeScale = resizeScale
        )
      compareCanvas.release()

      val actualFile = if (roborazziRecordingEnabled()) {
        // If record option is enabled, we should save the actual file as the golden file.
        goldenFile
      } else {
        File(
          roborazziOptions.compareOptions.outputDirectoryPath,
          goldenFile.nameWithoutExtension + "_actual." + goldenFile.extension
        )
      }
      canvas
        .save(
          file = actualFile,
          resizeScale = resizeScale
        )
      if (goldenFile.exists()) {
        CompareReportCaptureResult.Changed(
          compareFile = compareFile,
          actualFile = actualFile,
          goldenFile = goldenFile,
          timestampNs = System.nanoTime(),
        )
      } else {
        CompareReportCaptureResult.Added(
          compareFile = compareFile,
          actualFile = actualFile,
          goldenFile = goldenFile,
          timestampNs = System.nanoTime(),
        )
      }
    } else {
      CompareReportCaptureResult.Unchanged(
        goldenFile = goldenFile,
        timestampNs = System.nanoTime(),
      )
    }
    debugLog {
      "processOutputImageAndReport: \n" +
        "  goldenFile: $goldenFile\n" +
        "  changed: $changed\n" +
        "  result: $result\n"
    }
    roborazziOptions.compareOptions.roborazziCompareReporter.report(result)
  } else {
    // roborazzi.record is checked before
    canvas.save(goldenFile, resizeScale)
    debugLog {
      "processOutputImageAndReport: \n" +
        "  goldenFile: $goldenFile\n"
    }
  }
}

private fun log(message: String) {
  println("Roborazzi: $message")
}
