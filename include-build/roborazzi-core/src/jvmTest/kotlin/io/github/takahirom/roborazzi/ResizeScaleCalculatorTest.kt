package io.github.takahirom.roborazzi

import com.dropbox.differ.ImageComparator
import com.github.takahirom.roborazzi.CaptureResult
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.ImageIoFormat
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.ResizeScaleCalculator
import com.github.takahirom.roborazzi.RoboCanvas
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.processOutputImageAndReport
import com.github.takahirom.roborazzi.resizeScaleFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(InternalRoborazziApi::class, ExperimentalRoborazziApi::class)
class ResizeScaleCalculatorTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private class RecordingRoboCanvas(
    override val croppedWidth: Int,
    override val croppedHeight: Int,
    private val pixelDifferences: Int = 0,
  ) : RoboCanvas {
    override val width: Int = croppedWidth
    override val height: Int = croppedHeight

    val savedScales = mutableListOf<Double>()
    val differScales = mutableListOf<Double>()

    override fun save(
      path: String,
      resizeScale: Double,
      contextData: Map<String, Any>,
      imageIoFormat: ImageIoFormat,
    ) {
      savedScales += resizeScale
      File(path).apply { parentFile?.mkdirs() }.writeText("fake image")
    }

    override fun differ(
      other: RoboCanvas,
      resizeScale: Double,
      imageComparator: ImageComparator,
    ): ImageComparator.ComparisonResult {
      differScales += resizeScale
      return ImageComparator.ComparisonResult(
        pixelDifferences = pixelDifferences,
        pixelCount = other.width * other.height,
        width = other.width,
        height = other.height,
      )
    }

    override fun release() {}
  }

  private val halveLargeImages = ResizeScaleCalculator { size ->
    if (size.width * size.height > 1_000_000) 0.5 else 1.0
  }

  @Test
  fun recordSavesTheGoldenWithTheCalculatedScale() {
    val requestedSizes = mutableListOf<ResizeScaleCalculator.Size>()
    val canvas = RecordingRoboCanvas(croppedWidth = 1080, croppedHeight = 2400)

    processOutputImageAndReport(
      newRoboCanvas = canvas,
      goldenFile = File(temporaryFolder.root, "large.png"),
      roborazziOptions = options(
        taskType = RoborazziTaskType.Record,
        resizeScaleCalculator = { size ->
          requestedSizes += size
          halveLargeImages.calculate(size)
        },
      ),
      emptyCanvasFactory = { _, _, _, _ -> error("not used when recording") },
      canvasFactoryFromFile = { _, _ -> error("not used when recording") },
      comparisonCanvasFactory = { _, _, _, _ -> error("not used when recording") },
    )

    assertEquals(listOf(ResizeScaleCalculator.Size(width = 1080, height = 2400)), requestedSizes)
    assertEquals(listOf(0.5), canvas.savedScales)
  }

  @Test
  fun smallImagesKeepTheirScale() {
    val canvas = RecordingRoboCanvas(croppedWidth = 200, croppedHeight = 100)

    processOutputImageAndReport(
      newRoboCanvas = canvas,
      goldenFile = File(temporaryFolder.root, "small.png"),
      roborazziOptions = options(
        taskType = RoborazziTaskType.Record,
        resizeScaleCalculator = halveLargeImages,
      ),
      emptyCanvasFactory = { _, _, _, _ -> error("not used when recording") },
      canvasFactoryFromFile = { _, _ -> error("not used when recording") },
      comparisonCanvasFactory = { _, _, _, _ -> error("not used when recording") },
    )

    assertEquals(listOf(1.0), canvas.savedScales)
  }

  @Test
  fun compareUsesTheCalculatedScaleForTheGoldenSizeAndTheDiffer() {
    val goldenFile = File(temporaryFolder.root, "large.png").apply { writeText("golden") }
    val canvas = RecordingRoboCanvas(croppedWidth = 1080, croppedHeight = 2400)
    var result: CaptureResult? = null

    processOutputImageAndReport(
      newRoboCanvas = canvas,
      goldenFile = goldenFile,
      roborazziOptions = options(
        taskType = RoborazziTaskType.Compare,
        resizeScaleCalculator = halveLargeImages,
        onReport = { result = it },
      ),
      emptyCanvasFactory = { _, _, _, _ -> error("the golden exists") },
      canvasFactoryFromFile = { _, _ -> RecordingRoboCanvas(croppedWidth = 540, croppedHeight = 1200) },
      comparisonCanvasFactory = { _, _, _, _ -> error("the images are unchanged") },
    )

    assertEquals(listOf(0.5), canvas.differScales)
    assertTrue("Expected Unchanged but was $result", result is CaptureResult.Unchanged)
  }

  @Test
  fun changedImagesAreSavedWithTheCalculatedScale() {
    val goldenFile = File(temporaryFolder.root, "large.png").apply { writeText("golden") }
    val canvas = RecordingRoboCanvas(croppedWidth = 1080, croppedHeight = 2400, pixelDifferences = 10)
    val comparisonCanvas = RecordingRoboCanvas(croppedWidth = 1620, croppedHeight = 1200)
    val comparisonScales = mutableListOf<Double>()

    processOutputImageAndReport(
      newRoboCanvas = canvas,
      goldenFile = goldenFile,
      roborazziOptions = options(
        taskType = RoborazziTaskType.Compare,
        resizeScaleCalculator = halveLargeImages,
      ),
      emptyCanvasFactory = { _, _, _, _ -> error("the golden exists") },
      canvasFactoryFromFile = { _, _ -> RecordingRoboCanvas(croppedWidth = 540, croppedHeight = 1200) },
      comparisonCanvasFactory = { _, _, resizeScale, _ ->
        comparisonScales += resizeScale
        comparisonCanvas
      },
    )

    assertEquals(listOf(0.5), comparisonScales)
    assertEquals(listOf(0.5), comparisonCanvas.savedScales)
    assertEquals(listOf(0.5), canvas.savedScales)
  }

  @Test
  fun withoutACalculatorTheFixedResizeScaleIsUsed() {
    val recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.25)

    assertEquals(0.25, recordOptions.resizeScaleFor(width = 1080, height = 2400), 0.0)
  }

  @Test
  fun aNonPositiveScaleIsRejectedWithTheValueAndSize() {
    val recordOptions = RoborazziOptions.RecordOptions(resizeScaleCalculator = { 0.0 })

    val exception = assertThrows(IllegalArgumentException::class.java) {
      recordOptions.resizeScaleFor(width = 1080, height = 2400)
    }

    assertTrue(exception.message!!, exception.message!!.contains("0.0"))
    assertTrue(exception.message!!, exception.message!!.contains("1080x2400"))
  }

  @Test
  fun negativeAndNonFiniteScalesAreRejected() {
    listOf(-0.5, Double.NaN, Double.POSITIVE_INFINITY).forEach { invalidScale ->
      val recordOptions = RoborazziOptions.RecordOptions(resizeScaleCalculator = { invalidScale })

      assertThrows("$invalidScale must be rejected", IllegalArgumentException::class.java) {
        recordOptions.resizeScaleFor(width = 10, height = 10)
      }
    }
  }

  private fun options(
    taskType: RoborazziTaskType,
    resizeScaleCalculator: ResizeScaleCalculator,
    onReport: (CaptureResult) -> Unit = {},
  ) = RoborazziOptions(
    taskType = taskType,
    compareOptions = RoborazziOptions.CompareOptions(
      outputDirectoryPath = temporaryFolder.root.absolutePath,
    ),
    recordOptions = RoborazziOptions.RecordOptions(resizeScaleCalculator = resizeScaleCalculator),
    reportOptions = RoborazziOptions.ReportOptions(
      captureResultReporter = object : RoborazziOptions.CaptureResultReporter {
        override fun report(captureResult: CaptureResult, roborazziTaskType: RoborazziTaskType) {
          onReport(captureResult)
        }
      }
    ),
  )
}
