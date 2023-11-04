package com.github.takahirom.roborazzi

import com.dropbox.differ.ImageComparator
import com.dropbox.differ.SimpleImageComparator
import java.io.File
import java.io.FileWriter

const val DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH = "build/outputs/roborazzi"
var ROBORAZZI_DEBUG = false
fun roborazziEnabled(): Boolean {
  val isEnabled = roborazziRecordingEnabled() ||
    roborazziCompareEnabled() ||
    roborazziVerifyEnabled()
  debugLog {
    "roborazziEnabled: $isEnabled \n" +
      "roborazziRecordingEnabled(): ${roborazziRecordingEnabled()}\n" +
      "roborazziCompareEnabled(): ${roborazziCompareEnabled()}\n" +
      "roborazziVerifyEnabled(): ${roborazziVerifyEnabled()}\n" +
      "roborazziDefaultResizeScale(): ${roborazziDefaultResizeScale()}\n" +
      "roborazziDefaultNamingStrategy(): ${roborazziDefaultNamingStrategy()}\n" +
      "roborazziRecordFilePathStrategy(): ${roborazziRecordFilePathStrategy()}\n" +
      "RoborazziContext: ${provideRoborazziContext()}\n"
  }
  return isEnabled
}

fun roborazziRecordingEnabled(): Boolean {
  return System.getProperty("roborazzi.test.record") == "true"
}

fun roborazziCompareEnabled(): Boolean {
  return System.getProperty("roborazzi.test.compare") == "true"
}

fun roborazziVerifyEnabled(): Boolean {
  return System.getProperty("roborazzi.test.verify") == "true"
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
    val outputDirectoryPath: String = DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH,
    val imageComparator: ImageComparator = DefaultImageComparator,
    val resultValidator: (result: ImageComparator.ComparisonResult) -> Boolean = DefaultResultValidator,
  ) {
    constructor(
      outputDirectoryPath: String = DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH,
      /**
       * This value determines the threshold of pixel change at which the diff image is output or not.
       * The value should be between 0 and 1
       */
      changeThreshold: Float = 0F,
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
    fun report(reportResult: CaptureResult)

    companion object {
      operator fun invoke(): CaptureResultReporter {
        return if (roborazziVerifyEnabled()) {
          VerifyCaptureResultReporter()
        } else {
          JsonOutputCaptureResultReporter()
        }
      }
    }

    class JsonOutputCaptureResultReporter : CaptureResultReporter {

      init {
        File(RoborazziReportConst.resultDirPath).mkdirs()
      }

      override fun report(reportResult: CaptureResult) {
        val absolutePath = File(RoborazziReportConst.resultDirPath).absolutePath
        val nameWithoutExtension = when (reportResult) {
          is CaptureResult.Added -> reportResult.compareFile
          is CaptureResult.Changed -> reportResult.goldenFile
          is CaptureResult.Unchanged -> reportResult.goldenFile
          is CaptureResult.Recorded -> reportResult.goldenFile
        }.nameWithoutExtension
        val reportFileName =
          "$absolutePath/${reportResult.timestampNs}_$nameWithoutExtension.json"

        val jsonResult = reportResult.toJson()
        FileWriter(reportFileName).use { it.write(jsonResult.toString()) }
        debugLog { "JsonResult file($reportFileName) has been written" }
      }
    }

    @InternalRoborazziApi
    class VerifyCaptureResultReporter : CaptureResultReporter {
      private val jsonOutputCaptureResultReporter = JsonOutputCaptureResultReporter()
      override fun report(reportResult: CaptureResult) {
        jsonOutputCaptureResultReporter.report(reportResult)
        val assertErrorOrNull = getAssertErrorOrNull(reportResult)
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

private fun getAssertErrorOrNull(reportResult: CaptureResult): AssertionError? =
  when (reportResult) {
    is CaptureResult.Added -> AssertionError(
      "Roborazzi: The original file(${reportResult.goldenFile.absolutePath}) was not found.\n" +
        "See the actual image at ${reportResult.actualFile.absolutePath}"
    )

    is CaptureResult.Changed -> AssertionError(
      "Roborazzi: ${reportResult.goldenFile.absolutePath} is changed.\n" +
        "See the compare image at ${reportResult.compareFile.absolutePath}"
    )

    is CaptureResult.Unchanged, is CaptureResult.Recorded -> {
      null
    }
  }