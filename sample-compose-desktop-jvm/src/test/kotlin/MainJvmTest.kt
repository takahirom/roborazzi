import androidx.compose.ui.test.*
import com.github.takahirom.roborazzi.ROBORAZZI_DEBUG
import com.github.takahirom.roborazzi.RoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

class MainJvmTest {
  @OptIn(ExperimentalTestApi::class)
  @Test
  fun test() {
    ROBORAZZI_DEBUG = true
    runDesktopComposeUiTest {
      setContent {
        App()
      }
      val roborazziOptions = RoborazziOptions(
        // Desktop Compose renders real fonts on the host JVM, so anti-aliasing /
        // sub-pixel rendering can differ slightly between the recording runner and
        // the comparing runner. Allow a tiny tolerance to avoid spurious diffs.
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01F)
      )
      onRoot().captureRoboImage(roborazziOptions = roborazziOptions)

      onNodeWithTag("button").performClick()

      onRoot().captureRoboImage(roborazziOptions = roborazziOptions)
    }
  }
}
