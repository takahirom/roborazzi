package io.github.takahirom.roborazzi

import com.dropbox.differ.ImageComparator
import com.github.takahirom.roborazzi.CaptureResult
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.ImageIoFormat
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.RoboCanvas
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.processOutputImageAndReport
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(InternalRoborazziApi::class, ExperimentalRoborazziApi::class)
class ProcessOutputImageAndReportTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  /**
   * A canvas that writes a real (empty) file when saved so that the golden file
   * exists on disk after a recording task saves the actual image to it.
   */
  private class FakeRoboCanvas : RoboCanvas {
    override val croppedWidth: Int = 100
    override val croppedHeight: Int = 100
    override val width: Int = 100
    override val height: Int = 100

    override fun save(
      path: String,
      resizeScale: Double,
      contextData: Map<String, Any>,
      imageIoFormat: ImageIoFormat,
    ) {
      File(path).apply { parentFile?.mkdirs() }.writeText("fake image")
    }

    override fun differ(
      other: RoboCanvas,
      resizeScale: Double,
      imageComparator: ImageComparator
    ): ImageComparator.ComparisonResult {
      // Report a difference so the result is treated as changed rather than unchanged.
      return ImageComparator.ComparisonResult(
        pixelDifferences = 100,
        pixelCount = 100,
        width = 100,
        height = 100,
      )
    }

    override fun release() {}
  }

  @Test
  fun reservedCompareSuffixIsRejected() {
    val exception = assertThrows(IllegalArgumentException::class.java) {
      processOutputImageAndReport(
        newRoboCanvas = FakeRoboCanvas(),
        goldenFile = File("golden_compare.png"),
        roborazziOptions = RoborazziOptions(),
        emptyCanvasFactory = { _, _, _, _ -> error("emptyCanvasFactory should not be reached") },
        canvasFactoryFromFile = { _, _ -> error("canvasFactoryFromFile should not be reached") },
        comparisonCanvasFactory = { _, _, _, _ -> error("comparisonCanvasFactory should not be reached") },
      )
    }
    assertTrue(
      "message should mention the reserved suffix",
      exception.message?.contains("_compare") == true,
    )
  }

  @Test
  fun reservedActualSuffixIsRejected() {
    assertThrows(IllegalArgumentException::class.java) {
      processOutputImageAndReport(
        newRoboCanvas = FakeRoboCanvas(),
        goldenFile = File("golden_actual.png"),
        roborazziOptions = RoborazziOptions(),
        emptyCanvasFactory = { _, _, _, _ -> error("emptyCanvasFactory should not be reached") },
        canvasFactoryFromFile = { _, _ -> error("canvasFactoryFromFile should not be reached") },
        comparisonCanvasFactory = { _, _, _, _ -> error("comparisonCanvasFactory should not be reached") },
      )
    }
  }

  @Test
  fun whenGoldenFileDoesNotExistInRecordingTaskResultShouldBeAdded() {
    val goldenFile = File(temporaryFolder.root, "golden.png")
    assertTrue("golden file must not exist before the test", !goldenFile.exists())

    var reportedResult: CaptureResult? = null
    val roborazziOptions = RoborazziOptions(
      taskType = RoborazziTaskType.CompareAndRecord,
      compareOptions = RoborazziOptions.CompareOptions(
        outputDirectoryPath = temporaryFolder.root.absolutePath,
      ),
      reportOptions = RoborazziOptions.ReportOptions(
        captureResultReporter = object : RoborazziOptions.CaptureResultReporter {
          override fun report(
            captureResult: CaptureResult,
            roborazziTaskType: RoborazziTaskType
          ) {
            reportedResult = captureResult
          }
        }
      )
    )

    processOutputImageAndReport(
      newRoboCanvas = FakeRoboCanvas(),
      goldenFile = goldenFile,
      roborazziOptions = roborazziOptions,
      emptyCanvasFactory = { _, _, _, _ -> FakeRoboCanvas() },
      canvasFactoryFromFile = { _, _ -> FakeRoboCanvas() },
      comparisonCanvasFactory = { _, _, _, _ -> FakeRoboCanvas() },
    )

    assertTrue(
      "Expected CaptureResult.Added for a brand-new golden but was ${reportedResult?.let { it::class.simpleName }}",
      reportedResult is CaptureResult.Added
    )
  }
}
