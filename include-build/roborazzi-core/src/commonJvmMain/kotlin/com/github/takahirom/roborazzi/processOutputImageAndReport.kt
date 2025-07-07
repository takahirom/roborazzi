package com.github.takahirom.roborazzi

import com.dropbox.differ.ImageComparator
import java.io.File

fun interface EmptyCanvasFactory {
  operator fun invoke(
    width: Int,
    height: Int,
    filled: Boolean,
    bufferedImageType: Int
  ): RoboCanvas
}

fun interface CanvasFactoryFromFile {
  operator fun invoke(
    file: File,
    bufferedImageType: Int
  ): RoboCanvas
}

fun interface ComparisonCanvasFactory {
  operator fun invoke(
    goldenRoboCanvas: RoboCanvas,
    newRoboImage: RoboCanvas,
    resizeScale: Double,
    bufferedImageType: Int
  ): RoboCanvas
}

@InternalRoborazziApi
fun processOutputImageAndReport(
  newRoboCanvas: RoboCanvas,
  goldenFile: File,
  roborazziOptions: RoborazziOptions,
  emptyCanvasFactory: EmptyCanvasFactory,
  canvasFactoryFromFile: CanvasFactoryFromFile,
  comparisonCanvasFactory: ComparisonCanvasFactory,
) = measurePerformance("process_output_image_and_report") {
  val taskType = roborazziOptions.taskType
  roborazziDebugLog {
    "processOutputImageAndReport(): " +
      "taskType:" + taskType +
      "\ngoldenFile:${goldenFile.absolutePath}"
  }
  if (taskType.isEnabled() && !roborazziSystemPropertyTaskType().isEnabled()) {
    roborazziReportLog(
      "Roborazzi Warning:\n" +
        "You have specified '$taskType' without the necessary plugin configuration like roborazzi.test.record=true or ./gradlew recordRoborazziDebug.\n" +
        "This may complicate your screenshot testing process because the behavior is not changeable. And it doesn't allow Roborazzi plugin to generate test report.\n" +
        "Please ensure proper setup in gradle.properties or via Gradle tasks for optimal functionality."
    )
  }
  val forbiddenFileSuffixes = listOf("_compare", "_actual")
  forbiddenFileSuffixes.forEach {
    if (goldenFile.nameWithoutExtension.endsWith(it)) {
      throw IllegalArgumentException("The file name should not end with $it because it is reserved for Roborazzi")
    }
  }
  val recordOptions = roborazziOptions.recordOptions
  val resizeScale = recordOptions.resizeScale
  val contextData = buildContextData(roborazziOptions)
  if (taskType.isVerifying() || taskType.isComparing()) {
    val width = (newRoboCanvas.croppedWidth * resizeScale).toInt()
    val height = (newRoboCanvas.croppedHeight * resizeScale).toInt()
    val goldenRoboCanvas = if (goldenFile.exists()) {
      canvasFactoryFromFile(goldenFile, recordOptions.pixelBitConfig.toBufferedImageType())
    } else {
      emptyCanvasFactory(
        width = width,
        height = height,
        filled = true,
        bufferedImageType = recordOptions.pixelBitConfig.toBufferedImageType()
      )
    }

    // Only used by CaptureResult.Changed
    var diffPercentage: Float? = null

    val compareOptions = roborazziOptions.compareOptions
    val changed = if (height == goldenRoboCanvas.height && width == goldenRoboCanvas.width) {
      val comparisonResult: ImageComparator.ComparisonResult =
        newRoboCanvas.differ(
          other = goldenRoboCanvas,
          resizeScale = resizeScale,
          imageComparator = compareOptions.imageComparator
        )
      diffPercentage = comparisonResult.pixelDifferences.toFloat() / comparisonResult.pixelCount
      val changed = !compareOptions.resultValidator(comparisonResult)
      roborazziReportLog("${goldenFile.name} The differ result :$comparisonResult changed:$changed")
      changed
    } else {
      roborazziReportLog("${goldenFile.name} The image size is changed. actual = (${goldenRoboCanvas.width}, ${goldenRoboCanvas.height}), golden = (${newRoboCanvas.croppedWidth}, ${newRoboCanvas.croppedHeight})")
      true
    }

    val result: CaptureResult = if (changed) {
      val comparisonFile = File(
        compareOptions.outputDirectoryPath,
        goldenFile.nameWithoutExtension + "_compare." + goldenFile.extension
      )
      val comparisonCanvas = comparisonCanvasFactory(
        goldenRoboCanvas = goldenRoboCanvas,
        newRoboImage = newRoboCanvas,
        resizeScale = resizeScale,
        bufferedImageType = recordOptions.pixelBitConfig.toBufferedImageType()
      )
      comparisonCanvas
        .save(
          path = comparisonFile.absolutePath,
          resizeScale = resizeScale,
          contextData = contextData,
          imageIoFormat = recordOptions.imageIoFormat,
        )
      roborazziDebugLog {
        "processOutputImageAndReport(): compareCanvas is saved " +
          "compareFile:${comparisonFile.absolutePath}"
      }
      comparisonCanvas.release()

      val actualFile = if (taskType.isRecording()) {
        // If record option is enabled, we should save the actual file as the golden file.
        goldenFile
      } else {
        File(
          compareOptions.outputDirectoryPath,
          goldenFile.nameWithoutExtension + "_actual." + goldenFile.extension
        )
      }
      newRoboCanvas
        .save(
          path = actualFile.absolutePath,
          resizeScale = resizeScale,
          contextData = contextData,
          imageIoFormat = recordOptions.imageIoFormat,
        )
      val aiOptions = compareOptions.aiAssertionOptions
      val aiResult = if (aiOptions != null && aiOptions.aiAssertions.isNotEmpty()) {
        aiOptions.aiAssertionModel.assert(
          referenceImageFilePath = goldenFile.absolutePath,
          comparisonImageFilePath = comparisonFile.absolutePath,
          actualImageFilePath = actualFile.absolutePath,
          aiAssertionOptions = aiOptions
        )
      } else {
        null
      }
      roborazziDebugLog {
        "processOutputImageAndReport(): actualCanvas is saved " +
          "actualFile:${actualFile.absolutePath}"
      }
      if (goldenFile.exists()) {
        CaptureResult.Changed(
          compareFile = comparisonFile.absolutePath,
          actualFile = actualFile.absolutePath,
          goldenFile = goldenFile.absolutePath,
          timestampNs = System.nanoTime(),
          diffPercentage = diffPercentage,
          aiAssertionResults = aiResult,
          contextData = contextData,
        )
      } else {
        CaptureResult.Added(
          compareFile = comparisonFile.absolutePath,
          actualFile = actualFile.absolutePath,
          goldenFile = goldenFile.absolutePath,
          timestampNs = System.nanoTime(),
          aiAssertionResults = aiResult,
          contextData = contextData,
        )
      }
    } else {
      CaptureResult.Unchanged(
        goldenFile = goldenFile.absolutePath,
        timestampNs = System.nanoTime(),
        contextData = contextData,
      )
    }
    roborazziDebugLog {
      "processOutputImageAndReport: \n" +
        "  goldenFile: $goldenFile\n" +
        "  changed: $changed\n" +
        "  result: $result\n"
    }
    roborazziOptions.reportOptions.captureResultReporter.report(
      captureResult = result,
      roborazziTaskType = taskType
    )
  } else {
    // roborazzi.record is checked before
    newRoboCanvas.save(
      path = goldenFile.absolutePath,
      resizeScale = resizeScale,
      contextData = contextData,
      imageIoFormat = recordOptions.imageIoFormat,
    )
    roborazziDebugLog {
      "processOutputImageAndReport: \n" +
        " record goldenFile: $goldenFile\n"
    }
    roborazziOptions.reportOptions.captureResultReporter.report(
      captureResult = CaptureResult.Recorded(
        goldenFile = goldenFile.absolutePath,
        timestampNs = System.nanoTime(),
        contextData = contextData,
      ),
      roborazziTaskType = taskType
    )
  }
}

@InternalRoborazziApi
private fun buildContextData(roborazziOptions: RoborazziOptions): Map<String, Any> =
  if (roborazziEnableContextData()) {
    val className = provideRoborazziContext().description?.className
    val classNameMap: Map<out String, Any> = className?.let {
      mapOf(
        RoborazziReportConst.DefaultContextData.DescriptionClass.key to className.toString()
      )
    } ?: mapOf()
    roborazziOptions.contextData + classNameMap
  } else {
    // This will be removed when we found if this is safe.
    mapOf()
  }
