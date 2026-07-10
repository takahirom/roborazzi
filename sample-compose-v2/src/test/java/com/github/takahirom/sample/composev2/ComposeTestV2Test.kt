package com.github.takahirom.sample.composev2

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziActivity
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.registerRoborazziActivityToRobolectricIfNeeded
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeTestV2Test {
  private val composeTestRule = createAndroidComposeRule<RoborazziActivity>()

  // RoborazziActivity is not declared in this sample's manifest, so register it to
  // Robolectric before the compose rule launches it, mirroring the custom tester setup.
  @get:Rule
  val ruleChain: TestRule = RuleChain
    .outerRule(object : TestWatcher() {
      override fun starting(description: Description) {
        super.starting(description)
        registerRoborazziActivityToRobolectricIfNeeded()
      }
    })
    .around(composeTestRule)
    .around(
      RoborazziRule(
        composeRule = composeTestRule,
        captureRoot = composeTestRule.onRoot(),
        options = RoborazziRule.Options(
          captureType = RoborazziRule.CaptureType.LastImage(),
        )
      )
    )

  @Test
  fun captureWithV2Rule() {
    composeTestRule.setContent {
      BasicText("Hello from Compose Test V2!")
    }
  }

  @Test
  fun manualCaptureWithV2Rule() {
    composeTestRule.setContent {
      BasicText("Manual capture with V2")
    }
    composeTestRule.onRoot().captureRoboImage()
  }
}
