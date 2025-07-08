package com.github.takahirom.roborazzi.sample.boxed

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.ROBORAZZI_DEBUG
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.provideRoborazziContext
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.github.takahirom.roborazzi.sample.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
  sdk = [35],
  qualifiers = RobolectricDeviceQualifiers.NexusOne
)
class FilePathTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @OptIn(InternalRoborazziApi::class)
  @Test
  fun relativePathShouldNotHaveDuplicatedPath() {
    boxedEnvironment {
      ROBORAZZI_DEBUG = true
      val expectedOutput =
        File("${roborazziSystemPropertyOutputDirectory()}/${this::class.qualifiedName}.relativePathShouldNotHaveDuplicatedPath.png")
      expectedOutput.delete()
      System.setProperty(
        "roborazzi.test.record",
        "true"
      )
      System.setProperty(
        "roborazzi.record.filePathStrategy",
        "relativePathFromRoborazziContextOutputDirectory"
      )
      provideRoborazziContext().setRuleOverrideOutputDirectory("build/outputs/roborazzi")

      onView(ViewMatchers.isRoot())
        .captureRoboImage()

      assert(
        expectedOutput
          .exists()
      ) {
        "File not found: ${expectedOutput.absolutePath} \n"
      }
    }
  }
}
