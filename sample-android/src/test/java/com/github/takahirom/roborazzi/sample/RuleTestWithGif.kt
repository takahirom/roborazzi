package com.github.takahirom.roborazzi.sample

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ROBORAZZI_DEBUG
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
  sdk = [30],
  fontScale = 2.0f,
  qualifiers = RobolectricDeviceQualifiers.Pixel4XL,
)
class RuleTestWithGif {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val roborazziRule = RoborazziRule(
    composeTestRule,
    captureRoot = composeTestRule.onRoot(),
    options = RoborazziRule.Options(RoborazziRule.CaptureType.Gif())
  )
  init {
    ROBORAZZI_DEBUG = true
  }

  @Test
  fun captureRoboGifSample() {
    // launch
//    ActivityScenario.launch(MainActivity::class.java)
    // move to next page
//    onView(withId(R.id.button_first))
//      .perform(click())
    composeTestRule.onNodeWithTag("MyComposeButton").performClick()
    // back
    composeTestRule.onNodeWithTag("MyComposeButton").performClick()
    // move to next page
    composeTestRule.onNodeWithTag("MyComposeButton").performClick()
  }
}

