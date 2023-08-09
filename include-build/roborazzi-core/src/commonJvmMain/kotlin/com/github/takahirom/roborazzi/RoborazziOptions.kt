package com.github.takahirom.roborazzi

import com.dropbox.differ.ImageComparator
import com.github.takahirom.roborazzi.DefaultFileNameGenerator
import com.github.takahirom.roborazzi.ThresholdValidator
import com.github.takahirom.roborazzi.debugLog
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
  val compareOptions: CompareOptions = CompareOptions(),
  val recordOptions: RecordOptions = RecordOptions(),
) {
  interface CaptureType {
    class Screenshot : CaptureType {
      override fun shouldTakeScreenshot(): kotlin.Boolean {
        return true
      }
    }

    fun shouldTakeScreenshot(): Boolean

    companion object
  }

  data class CompareOptions(
    val roborazziCompareReporter: RoborazziCompareReporter = RoborazziCompareReporter(),
    val outputDirectoryPath: String = DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH,
    val resultValidator: (result: ImageComparator.ComparisonResult) -> Boolean,
  ) {
    constructor(
      roborazziCompareReporter: RoborazziCompareReporter = RoborazziCompareReporter(),
      outputDirectoryPath: String = DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH,
      /**
       * This value determines the threshold of pixel change at which the diff image is output or not.
       * The value should be between 0 and 1
       */
      changeThreshold: Float = 0.01F,
    ) : this(
      roborazziCompareReporter = roborazziCompareReporter,
      outputDirectoryPath = outputDirectoryPath,
      resultValidator = ThresholdValidator(changeThreshold)
    )
  }

  interface RoborazziCompareReporter {
    fun report(compareReportCaptureResult: CompareReportCaptureResult)

    companion object {
      operator fun invoke(): RoborazziCompareReporter {
        return if (roborazziVerifyEnabled()) {
          VerifyRoborazziCompareReporter()
        } else {
          JsonOutputRoborazziCompareReporter()

        }
      }
    }

    class JsonOutputRoborazziCompareReporter : RoborazziCompareReporter {

      init {
        File(RoborazziReportConst.compareReportDirPath).mkdirs()
      }

      override fun report(compareReportCaptureResult: CompareReportCaptureResult) {
        val absolutePath = File(RoborazziReportConst.compareReportDirPath).absolutePath
        val nameWithoutExtension = when (compareReportCaptureResult) {
          is CompareReportCaptureResult.Added -> compareReportCaptureResult.compareFile
          is CompareReportCaptureResult.Changed -> compareReportCaptureResult.goldenFile
          is CompareReportCaptureResult.Unchanged -> compareReportCaptureResult.goldenFile
        }.nameWithoutExtension
        val reportFileName =
          "$absolutePath/${compareReportCaptureResult.timestampNs}_$nameWithoutExtension.json"

        val jsonResult = compareReportCaptureResult.toJson()
        FileWriter(reportFileName).use { it.write(jsonResult.toString()) }
      }
    }

    class VerifyRoborazziCompareReporter : RoborazziCompareReporter {
      override fun report(compareReportCaptureResult: CompareReportCaptureResult) {
        when (compareReportCaptureResult) {
          is CompareReportCaptureResult.Added -> throw AssertionError(
            "Roborazzi: The original file(${compareReportCaptureResult.goldenFile.absolutePath}) was not found.\n" +
              "See the actual image at ${compareReportCaptureResult.actualFile.absolutePath}"
          )

          is CompareReportCaptureResult.Changed -> throw AssertionError(
            "Roborazzi: ${compareReportCaptureResult.goldenFile.absolutePath} is changed.\n" +
              "See the compare image at ${compareReportCaptureResult.compareFile.absolutePath}"
          )

          is CompareReportCaptureResult.Unchanged -> {
          }
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
