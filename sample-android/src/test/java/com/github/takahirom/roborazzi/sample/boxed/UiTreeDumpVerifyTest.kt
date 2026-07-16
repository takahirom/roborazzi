package com.github.takahirom.roborazzi.sample.boxed

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.UiTreeDumpOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyCompareOutputDirectory
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Proves the UI tree dump never affects verification: with the feature on, a
 * verify of an unchanged screenshot still passes, and the sidecar for the
 * current (verify) run is written next to the `_actual` image path.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel4, sdk = [35])
class UiTreeDumpVerifyTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun verifyOfUnchangedScreenshotPassesAndRewritesSidecar() {
    boxedEnvironment {
      composeTestRule.setContent {
        Text(
          text = "Login",
          modifier = Modifier
            .testTag("login_button")
            .size(120.dp)
        )
      }

      val prefix =
        "${roborazziSystemPropertyOutputDirectory()}/${this::class.qualifiedName}.verifyUnchanged"
      val goldenImage = File("$prefix.png")
      val goldenSidecar = File("$prefix.uitree.json")
      val actualImage = File(
        "${roborazziSystemPropertyCompareOutputDirectory()}/${this::class.qualifiedName}.verifyUnchanged_actual.png"
      )
      val actualSidecar = File(
        "${roborazziSystemPropertyCompareOutputDirectory()}/${this::class.qualifiedName}.verifyUnchanged_actual.uitree.json"
      )
      listOf(goldenImage, goldenSidecar, actualImage, actualSidecar).forEach { it.delete() }

      val options = { taskType: RoborazziTaskType ->
        RoborazziOptions(
          taskType = taskType,
          uiTreeDumpOptions = UiTreeDumpOptions(),
        )
      }

      // 1) Record the golden image + golden sidecar.
      setupRoborazziSystemProperty(record = true)
      onView(isRoot()).captureRoboImage(file = goldenImage, roborazziOptions = options(RoborazziTaskType.Record))
      assertTrue("golden image should exist", goldenImage.exists())
      assertTrue("golden sidecar should exist", goldenSidecar.exists())

      // 2) Verify the unchanged screenshot: must NOT throw (verification passes).
      setupRoborazziSystemProperty(verify = true)
      onView(isRoot()).captureRoboImage(file = goldenImage, roborazziOptions = options(RoborazziTaskType.Verify))

      // The image is unchanged, so no _actual image is written...
      assertFalse("unchanged verify must not write an _actual image", actualImage.exists())
      // ...but the sidecar for the current run is still written.
      assertTrue("verify run should (re)write the _actual sidecar", actualSidecar.exists())

      // The sidecar is valid-ish and describes the same node.
      val json = actualSidecar.readText()
      val tagLines = json.lines().filter { it.contains("\"login_button\"") }
      assertEquals(1, tagLines.size)

      listOf(goldenImage, goldenSidecar, actualImage, actualSidecar).forEach { it.delete() }
    }
  }
}
