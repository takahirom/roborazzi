import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoboVideoOptions
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import io.github.takahirom.roborazzi.recordRoboVideo
import java.io.File
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Composable
private fun FadingContent() {
  var target by remember { mutableStateOf(0f) }
  LaunchedEffect(Unit) { target = 1f }
  val alpha by animateFloatAsState(target)
  Box(Modifier.width(400.dp).height(400.dp)) {
    Column(Modifier.testTag("preview").width(120.dp)) {
      // Static, so the very first frame already shows something that is not the background.
      Box(Modifier.width(120.dp).height(50.dp).background(Color.Blue))
      Box(Modifier.width(120.dp).height(100.dp).alpha(alpha).background(Color.Red))
    }
  }
}

@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
class RecordVideoDesktopTest {
  private val record = RoborazziOptions(taskType = RoborazziTaskType.Record)

  @Test
  fun recordsThePreviewBoundsFromTheFirstFrame() {
    val file = File(createTempDirectory().toFile(), "preview.gif")
    runDesktopComposeUiTest {
      recordRoboVideo(
        file = file,
        videoOptions = RoboVideoOptions(settleTimeoutMillis = 1000),
        roborazziOptions = record,
        node = { onNodeWithTag("preview") },
        setup = { setContent { FadingContent() } },
      )
    }
    assertTrue(file.exists(), "video should be written")
    val reader = ImageIO.getImageReadersByFormatName("gif").next()
    ImageIO.createImageInputStream(file).use { input ->
      reader.setInput(input)
      assertTrue(reader.getNumImages(true) > 1, "expected an animation")
      val first = reader.read(0)
      // Only the preview node is recorded, not the whole root.
      assertEquals(120, first.width)
      assertEquals(150, first.height)
      assertEquals(
        Color.Blue.toArgb(), first.getRGB(5, 5),
        "first frame should already show the blue box",
      )
    }
  }
}
