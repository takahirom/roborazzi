import androidx.compose.ui.test.*
import com.github.takahirom.roborazzi.ROBORAZZI_DEBUG
import com.github.takahirom.roborazzi.RoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicContainer.dynamicContainer
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.RepetitionInfo
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test

class MainJvmTest {
  @Test
  fun test() {
    runTest()
  }

  @ParameterizedTest
  @ValueSource(strings = ["first", "second"])
  fun parameterizedTest(value: String) {
    runTest("parameterized $value")
  }

  @RepeatedTest(2)
  fun repeatedTest(info: RepetitionInfo) {
    runTest("repeated ${info.currentRepetition}")
  }

  @TestFactory
  fun testFactory(): DynamicContainer = dynamicContainer(
    "container",
    listOf(
      dynamicTest("one") { runTest("dynamic one") },
      dynamicTest("two") { runTest("dynamic two") },
    )
  )

  @OptIn(ExperimentalTestApi::class)
  private fun runTest(value: String = "test") {
    ROBORAZZI_DEBUG = true
    runDesktopComposeUiTest {
      setContent {
        App(value)
      }
      val roborazziOptions = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0F)
      )
      onRoot().captureRoboImage(roborazziOptions = roborazziOptions)

      onNodeWithTag("button").performClick()

      onRoot().captureRoboImage(roborazziOptions = roborazziOptions)
    }
  }
}
