import androidx.compose.ui.test.*
import com.github.takahirom.roborazzi.ROBORAZZI_DEBUG
import com.github.takahirom.roborazzi.RoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

class MainTest {
  @OptIn(ExperimentalTestApi::class)
  @Test
  fun test() {
    ROBORAZZI_DEBUG = true
    runDesktopComposeUiTest {
      setContent {
        App()
      }
      val roborazziOptions = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0F)
      )
      onNode(isRoot())
        .captureToImage().captureRoboImage(roborazziOptions = roborazziOptions)
      onNodeWithTag("button").performClick()

      onNode(isRoot()).captureToImage().captureRoboImage(roborazziOptions = roborazziOptions)
    }
  }
}