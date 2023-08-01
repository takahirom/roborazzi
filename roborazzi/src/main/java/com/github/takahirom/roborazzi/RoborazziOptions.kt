package com.github.takahirom.roborazzi

import android.graphics.Bitmap
import com.dropbox.differ.ImageComparator
import io.github.takahirom.roborazzi.CompareReportCaptureResult
import io.github.takahirom.roborazzi.RoborazziReportConst
import java.io.File
import java.io.FileWriter

data class RoborazziOptions(
  val captureType: CaptureType = if (isNativeGraphicsEnabled()) CaptureType.Screenshot() else CaptureType.Dump(),
  val compareOptions: CompareOptions = CompareOptions(),
  val recordOptions: RecordOptions = RecordOptions(),
) {
  sealed interface CaptureType {
    class Screenshot : CaptureType

    data class Dump(
      val takeScreenShot: Boolean = isNativeGraphicsEnabled(),
      val basicSize: Int = 600,
      val depthSlideSize: Int = 30,
      val query: ((RoboComponent) -> Boolean)? = null,
      val explanation: ((RoboComponent) -> String?) = DefaultExplanation,
    ) : CaptureType {
      companion object {
        val DefaultExplanation: ((RoboComponent) -> String) = {
          it.text
        }
        val AccessibilityExplanation: ((RoboComponent) -> String) = {
          it.accessibilityText
        }

      }
    }
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
            "Roborazzi: ${compareReportCaptureResult.compareFile.absolutePath} is added.\n" +
              "See compare image at ${compareReportCaptureResult.compareFile.absolutePath}"
          )

          is CompareReportCaptureResult.Changed -> throw AssertionError(
            "Roborazzi: ${compareReportCaptureResult.goldenFile.absolutePath} is changed.\n" +
              "See compare image at ${compareReportCaptureResult.compareFile.absolutePath}"
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

    fun toBitmapConfig(): Bitmap.Config {
      return when (this) {
        Argb8888 -> Bitmap.Config.ARGB_8888
        Rgb565 -> Bitmap.Config.RGB_565
      }
    }

    fun toBufferedImageType(): Int {
      return when (this) {
        Argb8888 -> 2 // BufferedImage.TYPE_INT_ARGB
        Rgb565 -> 8 // BufferedImage.TYPE_USHORT_565_RGB
      }
    }
  }

  internal val shouldTakeBitmap: Boolean = when (captureType) {
    is CaptureType.Dump -> {
      if (captureType.takeScreenShot && !isNativeGraphicsEnabled()) {
        throw IllegalArgumentException("Please update Robolectric Robolectric 4.10 Alpha 1 and Add @GraphicsMode(GraphicsMode.Mode.NATIVE) or use takeScreenShot = false")
      }
      captureType.takeScreenShot
    }

    is CaptureType.Screenshot -> {
      if (!isNativeGraphicsEnabled()) {
        throw IllegalArgumentException("Please update Robolectric Robolectric 4.10 Alpha 1 and Add @GraphicsMode(GraphicsMode.Mode.NATIVE) or use CaptureType.Dump")
      }
      true
    }
  }
}