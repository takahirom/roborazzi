package com.github.takahirom.roborazzi

import com.dropbox.differ.ImageComparator
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
      "RoborazziContext: ${provideRoborazziContext()}\n"
  }
  return isEnabled
}

fun roborazziCompareEnabled(): Boolean {
  return System.getProperty("roborazzi.test.compare") == "true"
}

fun roborazziVerifyEnabled(): Boolean {
  return System.getProperty("roborazzi.test.verify") == "true"
}

fun roborazziRecordingEnabled(): Boolean {
  return System.getProperty("roborazzi.test.record") == "true"
}

fun roborazziDefaultResizeScale(): Double {
  return checkNotNull(System.getProperty("roborazzi.record.resizeScale", "1.0")).toDouble()
}

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

  data class ReportOptions(
    val captureResultReporter: CaptureResultReporter = CaptureResultReporter.defaultReporter(),
  )

  data class CompareOptions(
    val outputDirectoryPath: String = DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH,
    val resultValidator: (result: ImageComparator.ComparisonResult) -> Boolean,
  ) {
    constructor(
      outputDirectoryPath: String = DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH,
      /**
       * This value determines the threshold of pixel change at which the diff image is output or not.
       * The value should be between 0 and 1
       */
      changeThreshold: Float = 0.01F,
    ) : this(
      outputDirectoryPath = outputDirectoryPath,
      resultValidator = ThresholdValidator(changeThreshold)
    )
  }

  @ExperimentalRoborazziApi
  interface CaptureResultReporter {
    fun report(reportResult: CaptureResult)

    companion object {
      @ExperimentalRoborazziApi
      fun ruleReporter(): CaptureResultReporter {
        return if (roborazziVerifyEnabled()) {
          VerifyAfterTestCaptureResultReporter()
        } else {
          JsonOutputCaptureResultReporter()
        }
      }

      @ExperimentalRoborazziApi
      fun defaultReporter(): CaptureResultReporter {
        return DefaultCaptureResultReporter()
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

    class DefaultCaptureResultReporter : CaptureResultReporter, OnTestFinishedListener {
      @InternalRoborazziApi
      val delegatedReporter by lazy {
        provideRoborazziContext().ruleCaptureResultReporter ?: run {
          if (roborazziVerifyEnabled()) {
            VerifyImmediateCaptureResultReporter()
          } else {
            JsonOutputCaptureResultReporter()
          }
        }
      }

      override fun report(reportResult: CaptureResult) {
        delegatedReporter.report(reportResult)
      }

      override fun onTestFinished() {
        // This method is called only when RoborazziRule is used.
        if (delegatedReporter is OnTestFinishedListener) {
          (delegatedReporter as OnTestFinishedListener).onTestFinished()
        }
      }
    }

    @InternalRoborazziApi
    class VerifyImmediateCaptureResultReporter : CaptureResultReporter {
      private val jsonOutputCaptureResultReporter = JsonOutputCaptureResultReporter()
      override fun report(reportResult: CaptureResult) {
        jsonOutputCaptureResultReporter.report(reportResult)
        val assertErrorOrNull = getAssertErrorOrNull(reportResult)
        if (assertErrorOrNull != null) {
          throw assertErrorOrNull
        }
      }
    }

    @InternalRoborazziApi
    class VerifyAfterTestCaptureResultReporter : CaptureResultReporter, OnTestFinishedListener {
      private val jsonOutputCaptureResultReporter = JsonOutputCaptureResultReporter()
      private val captureResults = mutableListOf<Pair<CaptureResult, AssertionError?>>()

      override fun report(reportResult: CaptureResult) {
        jsonOutputCaptureResultReporter.report(reportResult)
        captureResults.add(reportResult to getAssertErrorOrNull(reportResult))
      }

      override fun onTestFinished() {
        val assertionErrorOrNull =
          captureResults.firstOrNull { it.first is CaptureResult.Added || it.first is CaptureResult.Changed }?.second
        if (assertionErrorOrNull != null) {
          throw assertionErrorOrNull
        }
        captureResults.clear()
      }
    }

    @ExperimentalRoborazziApi
    interface OnTestFinishedListener {
      fun onTestFinished()
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

private fun getAssertErrorOrNull(reportResult: CaptureResult): AssertionError? = when (reportResult) {
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