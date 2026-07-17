import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
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
}
