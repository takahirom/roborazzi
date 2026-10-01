package com.github.takahirom.roborazzi.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.UiTreeDumpOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * `capture.density` lets readers convert the px `bounds` to dp (`dp = px / density`).
 * tvdpi is used because its density is not a whole number: Android computes it in
 * float as 213 * 0.00625f, which is 1.3312501 rather than 1.33125.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w640dp-h360dp-land-tvdpi", sdk = [35])
class UiTreeDumpDensityTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val options = RoborazziOptions(
    taskType = RoborazziTaskType.Record,
    uiTreeDumpOptions = UiTreeDumpOptions(annotateImage = false),
  )

  @Before
  fun setContent() {
    composeTestRule.setContent {
      Text(text = "Login", modifier = Modifier.testTag("login_button").size(16.dp))
    }
  }

  @Test
  fun viewCaptureRecordsDensity() {
    assertDensity("view") { onView(isRoot()).captureRoboImage(file = it, roborazziOptions = options) }
  }

  @Test
  fun composeNodeCaptureRecordsDensity() {
    assertDensity("compose") {
      composeTestRule.onNodeWithTag("login_button").captureRoboImage(file = it, roborazziOptions = options)
    }
  }

  @Test
  fun screenCaptureRecordsDensity() {
    assertDensity("screen") { captureScreenRoboImage(file = it, roborazziOptions = options) }
  }

  private fun assertDensity(name: String, capture: (File) -> Unit) {
    val prefix = "${roborazziSystemPropertyOutputDirectory()}/${this::class.qualifiedName}.$name"
    val imageFile = File("$prefix.png")
    val sidecarFile = File("$prefix.uitree.json")
    imageFile.delete()
    sidecarFile.delete()

    capture(imageFile)

    val displayMetrics = composeTestRule.activity.resources.displayMetrics
    assertEquals(213, displayMetrics.densityDpi)
    val json = sidecarFile.readText()
    assertEquals(
      json,
      displayMetrics.density.toString(),
      JSONObject(json).getJSONObject("capture").get("density").toString(),
    )

    imageFile.delete()
    sidecarFile.delete()
  }
}
