package com.github.takahirom.roborazzi.sample

import androidx.compose.material.Text
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziActivity
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeTestV2Test {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<RoborazziActivity>()

  @get:Rule
  val roborazziRule = RoborazziRule(
    composeRule = composeTestRule,
    captureRoot = composeTestRule.onRoot(),
    options = RoborazziRule.Options(
      captureType = RoborazziRule.CaptureType.LastImage(),
    )
  )

  @Test
  fun captureWithV2Rule() {
    composeTestRule.setContent {
      Text("Hello from Compose Test V2!")
    }
  }

  @Test
  fun manualCaptureWithV2Rule() {
    composeTestRule.setContent {
      Text("Manual capture with V2")
    }
    composeTestRule.onRoot().captureRoboImage()
  }
}
