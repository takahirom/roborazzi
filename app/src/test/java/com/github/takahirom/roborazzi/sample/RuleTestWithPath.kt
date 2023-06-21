package com.github.takahirom.roborazzi.sample

import androidx.test.core.app.ActivityScenario.launch
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.RoborazziRule.CaptureType
import com.github.takahirom.roborazzi.RoborazziRule.Options
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RuleTestWithPath {
  @OptIn(ExperimentalRoborazziApi::class)
  @get:Rule
  val roborazziRule = RoborazziRule(
    captureRoot = onView(isRoot()),
    options = Options(
      captureType = CaptureType.None,
      outputDirectoryPath = "$DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/custom_path",
    ),
  )

  @Test
  fun captureRoboImage() {
    launch(MainActivity::class.java)
    onView(isRoot()).captureRoboImage()
  }
}
