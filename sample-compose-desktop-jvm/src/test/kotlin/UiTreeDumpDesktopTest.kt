import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.UiTreeDumpOptions
import io.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
class UiTreeDumpDesktopTest {

  private fun tempDir(name: String): File =
    File(System.getProperty("java.io.tmpdir"), "roborazzi-uitree-$name-${System.nanoTime()}")
      .apply { mkdirs() }

  @Test
  fun writesSidecarAndAnnotatedImageWhenEnabled() {
    val dir = tempDir("enabled")
    val golden = File(dir, "UiTreeDumpDesktop.png")
    val options = RoborazziOptions(
      taskType = RoborazziTaskType.Record,
      uiTreeDumpOptions = UiTreeDumpOptions(),
    )
    runDesktopComposeUiTest {
      setContent { App() }
      onRoot().captureRoboImage(file = golden, roborazziOptions = options)
    }

    assertTrue(golden.exists(), "screenshot should be recorded at ${golden.absolutePath}")

    val sidecar = File(dir, "UiTreeDumpDesktop.uitree.json")
    assertTrue(sidecar.exists(), "sidecar should be written at ${sidecar.absolutePath}")
    val json = sidecar.readText()
    assertTrue(json.contains("\"schemaVersion\": 1"), "sidecar should be valid UI tree JSON")
    assertTrue(json.contains("\"type\": \"compose\""), "sidecar should contain compose nodes")
    // The button node's testTag and bounds live on one grep-able line.
    val taggedLine = json.lines().firstOrNull {
      it.contains("\"testTag\": \"button\"")
    }
    assertTrue(taggedLine != null, "sidecar should contain the button testTag line")
    assertTrue(taggedLine.contains("\"bounds\": ["), "the testTag line should carry bounds")

    val annotated = File(dir, "UiTreeDumpDesktop.annotated.png")
    assertTrue(annotated.exists(), "annotated image should be written at ${annotated.absolutePath}")

    val goldenImage = ImageIO.read(golden)
    val annotatedImage = ImageIO.read(annotated)
    assertEquals(goldenImage.width, annotatedImage.width, "annotated width should match screenshot")
    assertEquals(goldenImage.height, annotatedImage.height, "annotated height should match screenshot")

    var differing = 0
    outer@ for (y in 0 until goldenImage.height) {
      for (x in 0 until goldenImage.width) {
        if (goldenImage.getRGB(x, y) != annotatedImage.getRGB(x, y)) {
          differing++
          if (differing > 0) break@outer
        }
      }
    }
    assertTrue(differing > 0, "annotated image should differ from the screenshot (boxes drawn)")
  }

  @Test
  fun writesOnlySidecarWhenAnnotateImageDisabled() {
    val dir = tempDir("no-annotate")
    val golden = File(dir, "UiTreeDumpDesktopNoAnnotate.png")
    val options = RoborazziOptions(
      taskType = RoborazziTaskType.Record,
      uiTreeDumpOptions = UiTreeDumpOptions(annotateImage = false),
    )
    runDesktopComposeUiTest {
      setContent { App() }
      onNodeWithTag("button").captureRoboImage(file = golden, roborazziOptions = options)
    }

    val sidecar = File(dir, "UiTreeDumpDesktopNoAnnotate.uitree.json")
    assertTrue(sidecar.exists(), "sidecar should still be written when annotateImage=false")
    val annotated = File(dir, "UiTreeDumpDesktopNoAnnotate.annotated.png")
    assertFalse(annotated.exists(), "no annotated image should be written when annotateImage=false")
  }

  /**
   * Regression coverage for a non-root capture: the captured node is offset from
   * the window origin, and it contains an inner node offset within it. The
   * annotated boxes must be drawn at coordinates LOCAL to the captured image
   * (`(raw - capturedRoot.origin) * scale`), not at window-absolute coordinates.
   *
   * The captured node is passed as the tree root, and `computeUiTreeAnnotations`
   * subtracts the root's origin, so the inner node's filled numbered label must
   * land at its LOCAL offset inside the crop, not shifted by the captured root's
   * window position.
   */
  @Test
  fun annotationBoxesUseImageLocalCoordinatesForNonRootCapture() {
    val dir = tempDir("non-root")
    val golden = File(dir, "UiTreeDumpDesktopNonRoot.png")
    val annotated = File(dir, "UiTreeDumpDesktopNonRoot.annotated.png")
    val options = RoborazziOptions(
      taskType = RoborazziTaskType.Record,
      uiTreeDumpOptions = UiTreeDumpOptions(),
    )

    // Offsets in dp; the container sits away from the window origin, and the
    // inner node sits away from the container origin.
    val containerWindowOffsetDp = 30 to 50
    val innerLocalOffsetDp = 40 to 24

    var rootWindowLeftPx = 0
    var rootWindowTopPx = 0
    var innerLocalLeftPx = 0
    var innerLocalTopPx = 0

    runDesktopComposeUiTest {
      setContent {
        Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
          Box(
            modifier = Modifier
              .offset(x = containerWindowOffsetDp.first.dp, y = containerWindowOffsetDp.second.dp)
              .testTag("container")
              .size(200.dp)
              .background(Color.LightGray)
          ) {
            Box(
              modifier = Modifier
                .offset(x = innerLocalOffsetDp.first.dp, y = innerLocalOffsetDp.second.dp)
                .testTag("inner")
                .size(60.dp)
                .background(Color.Red)
            )
          }
        }
      }
      val containerNode = onNodeWithTag("container").fetchSemanticsNode()
      val density = containerNode.layoutInfo.density
      rootWindowLeftPx = containerNode.boundsInWindow.left.toInt()
      rootWindowTopPx = containerNode.boundsInWindow.top.toInt()
      with(density) {
        innerLocalLeftPx = innerLocalOffsetDp.first.dp.roundToPx()
        innerLocalTopPx = innerLocalOffsetDp.second.dp.roundToPx()
      }
      onNodeWithTag("container").captureRoboImage(file = golden, roborazziOptions = options)
    }

    // The captured root really is offset from the window origin, so subtracting
    // the root origin is not a no-op (otherwise this test would prove nothing).
    assertTrue(
      rootWindowLeftPx > 0 && rootWindowTopPx > 0,
      "captured container should be offset from the window origin " +
        "(left=$rootWindowLeftPx, top=$rootWindowTopPx)",
    )

    assertTrue(golden.exists(), "screenshot should be recorded")
    assertTrue(annotated.exists(), "annotated image should be written")
    val goldenImage = ImageIO.read(golden)
    val annotatedImage = ImageIO.read(annotated)

    // The inner node's numbered label is a FILLED rectangle drawn at the box's
    // top-left corner. Count annotation pixels (annotated != golden) in a small
    // window at the LOCAL corner vs the (wrong) WINDOW-absolute corner.
    fun annotationPixelsIn(originX: Int, originY: Int, w: Int, h: Int): Int {
      var count = 0
      for (y in originY until (originY + h)) {
        for (x in originX until (originX + w)) {
          if (x < 0 || y < 0 || x >= annotatedImage.width || y >= annotatedImage.height) continue
          if (annotatedImage.getRGB(x, y) != goldenImage.getRGB(x, y)) count++
        }
      }
      return count
    }

    val windowStyle = 18
    val atLocalCorner = annotationPixelsIn(innerLocalLeftPx, innerLocalTopPx, windowStyle, windowStyle)
    val atWindowCorner = annotationPixelsIn(
      innerLocalLeftPx + rootWindowLeftPx,
      innerLocalTopPx + rootWindowTopPx,
      windowStyle,
      windowStyle,
    )

    assertTrue(
      atLocalCorner > 0,
      "inner node's box/label should be drawn at its LOCAL corner " +
        "($innerLocalLeftPx, $innerLocalTopPx); found $atLocalCorner annotation pixels",
    )
    assertTrue(
      atLocalCorner > atWindowCorner,
      "annotation must be image-local, not window-absolute: local corner had " +
        "$atLocalCorner annotation pixels but window-absolute corner had " +
        "$atWindowCorner",
    )
  }
}
