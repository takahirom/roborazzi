package com.github.takahirom.roborazzi

import com.dropbox.differ.ImageComparator
import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.CaptureResults.Companion.gson
import java.io.File
import java.io.FileWriter

const val DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH: String = "build/outputs/roborazzi"
var ROBORAZZI_DEBUG: Boolean = false

@ExperimentalRoborazziApi
fun roborazziSystemPropertyOutputDirectory(): String {
  return System.getProperty("roborazzi.output.dir", DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH)
}

@ExperimentalRoborazziApi
fun roborazziSystemPropertyResultDirectory(): String {
  return System.getProperty("roborazzi.result.dir",
    "build/${RoborazziReportConst.resultDirPathFromBuildDir}"
  )
}

@ExperimentalRoborazziApi
// This will be removed when we found if this is safe.
fun roborazziEnableContextData(): Boolean {
  return System.getProperty("roborazzi.contextdata", "true").toBoolean()
}

@Deprecated(
  message = "Use roborazziSystemPropertyTaskType()",
  replaceWith = ReplaceWith("roborazziSystemPropertyTaskType().isEnabled()"),
)
fun roborazziEnabled(): Boolean {
  return roborazziSystemPropertyTaskType().isEnabled()
}

@Deprecated(
  message = "Use roborazziSystemPropertyTaskType()",
  ReplaceWith("roborazziSystemPropertyTaskType().isRecord()")
)
fun roborazziRecordingEnabled(): Boolean {
  return roborazziSystemPropertyTaskType().isRecording()
}

@Deprecated(
  message = "Use roborazziSystemPropertyTaskType()",
  ReplaceWith("roborazziSystemPropertyTaskType().isComparing()")
)
fun roborazziCompareEnabled(): Boolean {
  return roborazziSystemPropertyTaskType().isComparing()
}

@Deprecated(
  message = "Use roborazziSystemPropertyTaskType()",
  ReplaceWith("roborazziSystemPropertyTaskType().isVerifying()")
)
fun roborazziVerifyEnabled(): Boolean {
  return roborazziSystemPropertyTaskType().isVerifying()
}

@ExperimentalRoborazziApi
fun roborazziSystemPropertyTaskType(): RoborazziTaskType {
  val result = run {
    val roborazziRecordingEnabled = System.getProperty("roborazzi.test.record") == "true"
    val roborazziCompareEnabled = System.getProperty("roborazzi.test.compare") == "true"
    val roborazziVerifyEnabled = System.getProperty("roborazzi.test.verify") == "true"
    RoborazziTaskType.of(
      isRecording = roborazziRecordingEnabled,
      isComparing = roborazziCompareEnabled,
      isVerifying = roborazziVerifyEnabled
    )
  }
  debugLog {
    "roborazziSystemPropertyTaskType():\n" +
      "roborazziTaskType: $result \n" +
      "roborazziDefaultResizeScale(): ${roborazziDefaultResizeScale()}\n" +
      "roborazziDefaultNamingStrategy(): ${roborazziDefaultNamingStrategy()}\n" +
      "roborazziRecordFilePathStrategy(): ${roborazziRecordFilePathStrategy()}\n" +
      "RoborazziContext: ${provideRoborazziContext()}\n"
  }
  return result
}

/**
 * Specify the file path strategy for the recorded image.
 * Default: roborazzi.record.filePathStrategy=relativePathFromCurrentDirectory
 * If set to relativePathFromRoborazziContextOutputDirectory, the file will be saved to the output directory specified by RoborazziRule.Options.outputDirectoryPath.
 */
fun roborazziDefaultResizeScale(): Double {
  return checkNotNull(System.getProperty("roborazzi.record.resizeScale", "1.0")).toDouble()
}

@ExperimentalRoborazziApi
sealed interface RoborazziRecordFilePathStrategy {
  val propertyValue: String

  object RelativePathFromCurrentDirectory : RoborazziRecordFilePathStrategy {
    override val propertyValue: String
      get() = "relativePathFromCurrentDirectory"
  }

  object RelativePathFromRoborazziContextOutputDirectory : RoborazziRecordFilePathStrategy {
    override val propertyValue: String
      get() = "relativePathFromRoborazziContextOutputDirectory"
  }
}


/**
 * Specify the naming strategy for the recorded image.
 * Default: roborazzi.record.namingStrategy=testPackageAndClassAndMethod
 * If set to testPackageAndClassAndMethod, the file name will be com.example.MyTest.testMethod.png
 * If set to escapedTestPackageAndClassAndMethod, the file name will be com_example_MyTest.testMethod.png
 * If set to testClassAndMethod, the file name will be MyTest.testMethod.png
 */
@ExperimentalRoborazziApi
fun roborazziRecordFilePathStrategy(): RoborazziRecordFilePathStrategy {
  return when (
    System.getProperty(
      "roborazzi.record.filePathStrategy",
      RoborazziRecordFilePathStrategy.RelativePathFromCurrentDirectory.propertyValue
    )
  ) {
    RoborazziRecordFilePathStrategy.RelativePathFromCurrentDirectory.propertyValue ->
      RoborazziRecordFilePathStrategy.RelativePathFromCurrentDirectory

    RoborazziRecordFilePathStrategy.RelativePathFromRoborazziContextOutputDirectory.propertyValue ->
      RoborazziRecordFilePathStrategy.RelativePathFromRoborazziContextOutputDirectory

    else -> RoborazziRecordFilePathStrategy.RelativePathFromCurrentDirectory
  }
}

/**
 * You can specify the naming strategy of the image to be recorded.
 * The default is roborazzi.record.namingStrategy=testPackageAndClassAndMethod
 * If you specify testPackageAndClassAndMethod, the file name will be com.example.MyTest.testMethod.png
 * If you specify escapedTestPackageAndClassAndMethod, the file name will be com_example_MyTest.testMethod.png
 * If you specify testClassAndMethod, the file name will be MyTest.testMethod.png
 */
fun roborazziDefaultNamingStrategy(): DefaultFileNameGenerator.DefaultNamingStrategy {
  return DefaultFileNameGenerator.DefaultNamingStrategy
    .fromOptionName(
      optionName = checkNotNull(
        System.getProperty(
          "roborazzi.record.namingStrategy",
          DefaultFileNameGenerator.DefaultNamingStrategy.TestPackageAndClassAndMethod.optionName
        )
      )
    )
}

data class RoborazziOptions(
  /**
   * This option, taskType, is experimental. So the API may change.
   * Please tell me your opinion about this option
   * https://github.com/takahirom/roborazzi/issues/215
   */
  val taskType: RoborazziTaskType = roborazziSystemPropertyTaskType(),
  val contextData: Map<String, Any> = emptyMap(),
  val captureType: CaptureType = if (canScreenshot()) CaptureType.Screenshot() else defaultCaptureType(),
  val reportOptions: ReportOptions = ReportOptions(),
  val compareOptions: CompareOptions = CompareOptions(),
  val recordOptions: RecordOptions = RecordOptions(),
) {
  interface CaptureType {
    class Screenshot : CaptureType {
      override fun shouldTakeScreenshot(): Boolean {
        return true
      }
    }

    fun shouldTakeScreenshot(): Boolean

    companion object
  }

  @ExperimentalRoborazziApi
  data class ReportOptions(
    val captureResultReporter: CaptureResultReporter = CaptureResultReporter(),
  )

  data class CompareOptions(
    val outputDirectoryPath: String = roborazziSystemPropertyOutputDirectory(),
    val imageComparator: ImageComparator = DefaultImageComparator,
    val comparisonStyle: ComparisonStyle = ComparisonStyle.Grid(),
    val resultValidator: (result: ImageComparator.ComparisonResult) -> Boolean = DefaultResultValidator,
  ) {
    @ExperimentalRoborazziApi
    sealed interface ComparisonStyle {
      @ExperimentalRoborazziApi
      data class Grid(
        val bigLineSpaceDp: Int? = 16,
        val smallLineSpaceDp: Int? = 4,
        val hasLabel: Boolean = true
      ) : ComparisonStyle

      object Simple : ComparisonStyle
    }

    constructor(
      outputDirectoryPath: String = roborazziSystemPropertyOutputDirectory(),
      /**
       * This value determines the threshold of pixel change at which the diff image is output or not.
       * The value should be between 0 and 1
       */
      changeThreshold: Float,
      imageComparator: ImageComparator = DefaultImageComparator,
    ) : this(
      outputDirectoryPath = outputDirectoryPath,
      resultValidator = ThresholdValidator(changeThreshold),
      imageComparator = imageComparator,
    )

    companion object {
      val DefaultImageComparator = SimpleImageComparator(maxDistance = 0.007F)
      val DefaultResultValidator = ThresholdValidator(0F)
    }
  }

  @ExperimentalRoborazziApi
  interface CaptureResultReporter {
    fun report(captureResult: CaptureResult, roborazziTaskType: RoborazziTaskType)

    companion object {
      operator fun invoke(): CaptureResultReporter {
        return DefaultCaptureResultReporter()
      }
    }

    class DefaultCaptureResultReporter : CaptureResultReporter {
      override fun report(captureResult: CaptureResult, roborazziTaskType: RoborazziTaskType) {
        if (roborazziTaskType.isVerifying()) {
          VerifyCaptureResultReporter().report(captureResult, roborazziTaskType)
        } else {
          JsonOutputCaptureResultReporter().report(captureResult, roborazziTaskType)
        }
      }
    }

    class JsonOutputCaptureResultReporter : CaptureResultReporter {

      init {
        File(roborazziSystemPropertyResultDirectory()).mkdirs()
      }

      override fun report(captureResult: CaptureResult, roborazziTaskType: RoborazziTaskType) {
        val absolutePath = File(roborazziSystemPropertyResultDirectory()).absolutePath
        val nameWithoutExtension = when (captureResult) {
          is CaptureResult.Added -> captureResult.compareFile
          is CaptureResult.Changed -> captureResult.goldenFile
          is CaptureResult.Unchanged -> captureResult.goldenFile
          is CaptureResult.Recorded -> captureResult.goldenFile
        }.nameWithoutExtension
        val reportFileName =
          "$absolutePath/${captureResult.timestampNs}_$nameWithoutExtension.json"

        val jsonResult = gson.toJson(captureResult)
        FileWriter(reportFileName).use { it.write(jsonResult.toString()) }
        debugLog { "JsonResult file($reportFileName) has been written" }
      }
    }

    @InternalRoborazziApi
    class VerifyCaptureResultReporter : CaptureResultReporter {
      private val jsonOutputCaptureResultReporter = JsonOutputCaptureResultReporter()
      override fun report(captureResult: CaptureResult, roborazziTaskType: RoborazziTaskType) {
        jsonOutputCaptureResultReporter.report(captureResult, roborazziTaskType)
        val assertErrorOrNull = getAssertErrorOrNull(captureResult)
        if (assertErrorOrNull != null) {
          throw assertErrorOrNull
        }
      }
    }
  }

  data class RecordOptions(
    val resizeScale: Double = roborazziDefaultResizeScale(),
    val applyDeviceCrop: Boolean = false,
    val pixelBitConfig: PixelBitConfig = PixelBitConfig.Argb8888,
  )

  enum class PixelBitConfig {
    Argb8888,
    Rgb565;

    fun toBufferedImageType(): Int {
      return when (this) {
        Argb8888 -> 2 // BufferedImage.TYPE_INT_ARGB
        Rgb565 -> 8 // BufferedImage.TYPE_USHORT_565_RGB
      }
    }
  }

  internal val shouldTakeBitmap: Boolean = captureType.shouldTakeScreenshot()
}

expect fun canScreenshot(): Boolean

expect fun defaultCaptureType(): RoborazziOptions.CaptureType

private fun getAssertErrorOrNull(captureResult: CaptureResult): AssertionError? =
  when (captureResult) {
    is CaptureResult.Added -> AssertionError(
      "Roborazzi: The original file(${captureResult.goldenFile.absolutePath}) was not found.\n" +
        "See the actual image at ${captureResult.actualFile.absolutePath}"
    )

    is CaptureResult.Changed -> AssertionError(
      "Roborazzi: ${captureResult.goldenFile.absolutePath} is changed.\n" +
        "See the compare image at ${captureResult.compareFile.absolutePath}"
    )

    is CaptureResult.Unchanged, is CaptureResult.Recorded -> {
      null
    }
  }