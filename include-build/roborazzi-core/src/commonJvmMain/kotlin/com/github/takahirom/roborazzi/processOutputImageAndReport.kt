package com.github.takahirom.roborazzi

import com.dropbox.differ.ImageComparator
import java.io.File

@InternalRoborazziApi
fun processOutputImageAndReport(
  newRoboCanvas: RoboCanvas,
  goldenFile: File,
  roborazziOptions: RoborazziOptions,
  canvasFactory: (width: Int, height: Int, filled: Boolean, imageType: Int) -> RoboCanvas,
  canvasFromFile: (File, imageType: Int) -> RoboCanvas,
  generateComparisonCanvas: RoboCanvas.(RoboCanvas, resizeScale: Double, imageType: Int) -> RoboCanvas,
) {
  debugLog {
    "processOutputImageAndReport(): " +
      "goldenFile:${goldenFile.absolutePath}"
  }
  val forbiddenFileSuffixes = listOf("_compare", "_actual")
  forbiddenFileSuffixes.forEach {
    if (goldenFile.nameWithoutExtension.endsWith(it)) {
      throw IllegalArgumentException("The file name should not end with $it because it is reserved for Roborazzi")
    }
  }
  val recordOptions = roborazziOptions.recordOptions
  val resizeScale = recordOptions.resizeScale
  val reportResult = if (roborazziCompareEnabled() || roborazziVerifyEnabled()) {
    val newWidth = (newRoboCanvas.croppedWidth * resizeScale).toInt()
    val newHeight = (newRoboCanvas.croppedHeight * resizeScale).toInt()
    val goldenRoboCanvas = if (goldenFile.exists()) {
      canvasFromFile(goldenFile, recordOptions.pixelBitConfig.toBufferedImageType())
    } else {
      canvasFactory(
        newWidth,
        newHeight,
        true,
        recordOptions.pixelBitConfig.toBufferedImageType()
      )
    }
    val result: CaptureResult = generateCaptureResultForCompareTasks(
      newRoboCanvas = newRoboCanvas,
      resizeScale = resizeScale,
      goldenFile = goldenFile,
      roborazziOptions = roborazziOptions,
      goldenRoboCanvas = goldenRoboCanvas,
      newWidth = newWidth,
      newHeight = newHeight
    )
    when (result) {
      is CaptureResult.Changed, is CaptureResult.Added -> {
        val comparisonCanvas = goldenRoboCanvas.generateComparisonCanvas(
          newRoboCanvas,
          resizeScale,
          recordOptions.pixelBitConfig.toBufferedImageType()
        )
        val comparisonFile = when (result) {
          is CaptureResult.Changed -> result.compareFile
          is CaptureResult.Added -> result.compareFile
          else -> throw IllegalStateException("Unexpected result type: $result")
        }
        val actualFile = when (result) {
          is CaptureResult.Changed -> result.actualFile
          is CaptureResult.Added -> result.actualFile
          else -> throw IllegalStateException("Unexpected result type: $result")
        }
        comparisonCanvas
          .save(
            file = comparisonFile,
            resizeScale = resizeScale
          )
        debugLog {
          "processOutputImageAndReport(): compareCanvas is saved " +
            "compareFile:${comparisonFile.absolutePath}"
        }
        comparisonCanvas.release()

        newRoboCanvas
          .save(
            file = actualFile,
            resizeScale = resizeScale
          )
        debugLog {
          "processOutputImageAndReport(): actualCanvas is saved " +
            "actualFile:${actualFile.absolutePath}"
        }
      }

      is CaptureResult.Unchanged -> {
        debugLog {
          "processOutputImageAndReport(): Unchanged " +
            "goldenFile:${goldenFile.absolutePath}"
        }
      }

      is CaptureResult.Recorded -> throw IllegalStateException("Unexpected result type: $result")
    }
    result
  } else {
    // roborazzi.record is checked before
    newRoboCanvas.save(goldenFile, resizeScale)
    debugLog {
      "processOutputImageAndReport: \n" +
        " record goldenFile: $goldenFile\n"
    }
    CaptureResult.Recorded(
      goldenFile = goldenFile,
      timestampNs = System.nanoTime()
    )
  }
  roborazziOptions.reportOptions.captureResultReporter.report(reportResult)
}

private fun generateCaptureResultForCompareTasks(
  newRoboCanvas: RoboCanvas,
  resizeScale: Double,
  goldenFile: File,
  roborazziOptions: RoborazziOptions,
  goldenRoboCanvas: RoboCanvas,
  newWidth: Int,
  newHeight: Int,
): CaptureResult {
  val changed = isCanvasChanged(
    goldenRoboCanvas = goldenRoboCanvas,
    newRoboCanvas = newRoboCanvas,
    newWidth = newWidth,
    newHeight = newHeight,
    resizeScale = resizeScale,
    roborazziOptions = roborazziOptions,
    goldenFile = goldenFile
  )

  val result: CaptureResult = if (changed) {
    val comparisonFile = File(
      roborazziOptions.compareOptions.outputDirectoryPath,
      goldenFile.nameWithoutExtension + "_compare." + goldenFile.extension
    )
    val actualFile = if (roborazziRecordingEnabled()) {
      // It is possible that users want to use Verify and Record at the same time.
      // If record option is enabled, we should save the actual file as the golden file.
      goldenFile
    } else {
      File(
        roborazziOptions.compareOptions.outputDirectoryPath,
        goldenFile.nameWithoutExtension + "_actual." + goldenFile.extension
      )
    }
    if (goldenFile.exists()) {
      CaptureResult.Changed(
        compareFile = comparisonFile,
        actualFile = actualFile,
        goldenFile = goldenFile,
        timestampNs = System.nanoTime(),
      )
    } else {
      CaptureResult.Added(
        compareFile = comparisonFile,
        actualFile = actualFile,
        goldenFile = goldenFile,
        timestampNs = System.nanoTime(),
      )
    }
  } else {
    CaptureResult.Unchanged(
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
  return result
}

private fun isCanvasChanged(
  goldenRoboCanvas: RoboCanvas,
  newRoboCanvas: RoboCanvas,
  newWidth: Int,
  newHeight: Int,
  resizeScale: Double,
  roborazziOptions: RoborazziOptions,
  goldenFile: File
): Boolean = if (newHeight == goldenRoboCanvas.height && newWidth == goldenRoboCanvas.width) {
  val comparisonResult: ImageComparator.ComparisonResult =
    newRoboCanvas.differ(goldenRoboCanvas, resizeScale, roborazziOptions.compareOptions.imageComparator)
  val changed = !roborazziOptions.compareOptions.resultValidator(comparisonResult)
  log("${goldenFile.name} The differ result :$comparisonResult changed:$changed")
  changed
} else {
  log("${goldenFile.name} The image size is changed. actual = (${goldenRoboCanvas.width}, ${goldenRoboCanvas.height}), golden = (${newRoboCanvas.croppedWidth}, ${newRoboCanvas.croppedHeight})")
  true
}

private fun log(message: String) {
  println("Roborazzi: $message")
}
