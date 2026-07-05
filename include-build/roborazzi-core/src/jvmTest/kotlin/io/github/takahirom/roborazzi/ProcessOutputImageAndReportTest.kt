package io.github.takahirom.roborazzi

import com.dropbox.differ.ImageComparator
import com.github.takahirom.roborazzi.ImageIoFormat
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.RoboCanvas
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.processOutputImageAndReport
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(InternalRoborazziApi::class)
class ProcessOutputImageAndReportTest {

  private class FakeRoboCanvas : RoboCanvas {
    override val croppedWidth: Int = 1
    override val croppedHeight: Int = 1
    override val width: Int = 1
    override val height: Int = 1
    override fun save(
      path: String,
      resizeScale: Double,
      contextData: Map<String, Any>,
      imageIoFormat: ImageIoFormat,
    ) = error("save should not be reached")

    override fun differ(
      other: RoboCanvas,
      resizeScale: Double,
      imageComparator: ImageComparator
    ): ImageComparator.ComparisonResult = error("differ should not be reached")

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
}
