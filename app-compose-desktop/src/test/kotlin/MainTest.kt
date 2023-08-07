import androidx.compose.ui.test.*
import com.github.takahirom.roborazzi.RoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

class MainTest {
  @OptIn(ExperimentalTestApi::class)
  @Test
  fun test() {
    runDesktopComposeUiTest {
      setContent {
        App()
      }
      onNode(isRoot()).captureToImage().captureRoboImage()
      onNodeWithTag("button").performClick()
      onNode(isRoot()).captureToImage().captureRoboImage(
        roborazziOptions = RoborazziOptions(
          compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0F)
        )
      )
    }
  }
}