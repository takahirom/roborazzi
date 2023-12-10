package com.github.takahirom.roborazzi.sample.boxed

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.*
import com.github.takahirom.roborazzi.sample.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
  sdk = [30],
  qualifiers = RobolectricDeviceQualifiers.NexusOne
)
class RoborazziTaskTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test(
    expected = AssertionError::class
  )
  fun roborazziOptionTask() {
    boxedEnvironment {
      ROBORAZZI_DEBUG = true
      val prefix = "$DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/${this::class.qualifiedName}.roborazziOptionTask"
      val expectedOutput =
        File("$prefix.png")
      val expectedCompareOutput = File("${prefix}_compare.png")
      expectedOutput.delete()
      setupRoborazziSystemProperty(
        compare = true
      )

      try {
        onView(ViewMatchers.isRoot())
          .captureRoboImage(
            roborazziOptions = RoborazziOptions(
              taskType = RoborazziTaskType.Verify
            )
          )
      } finally {
        expectedCompareOutput.delete()
      }
    }
  }

  @Test
  fun roborazziOptionTaskConvertCompare() {
    boxedEnvironment {
      ROBORAZZI_DEBUG = true
      val prefix = "$DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/${this::class.qualifiedName}.roborazziOptionTaskConvertCompare"
      val expectedCompareOutput = File("${prefix}_compare.png")
      expectedCompareOutput.delete()
      val expectedOutput = File("${prefix}.png")
      expectedOutput.delete()
      setupRoborazziSystemProperty(
        record = true,
        compare = true,
        verify = true
      )

      onView(ViewMatchers.isRoot())
        .captureRoboImage(
          roborazziOptions = RoborazziOptions(
            taskType = roborazziSystemPropertyTaskType().convertVerifyingToComparing()
          )
        )

      assert(
        expectedCompareOutput
          .exists()
      ) {
        "File not found: ${expectedCompareOutput.absolutePath} \n"
      }
      assert(
        expectedOutput
          .exists()
      ) {
        "File not found: ${expectedOutput.absolutePath} \n"
      }
      expectedCompareOutput.delete()
    }
  }
}
