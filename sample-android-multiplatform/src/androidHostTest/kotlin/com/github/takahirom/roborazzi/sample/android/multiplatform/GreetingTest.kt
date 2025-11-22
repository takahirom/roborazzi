package com.github.takahirom.roborazzi.sample.android.multiplatform

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tests for Roborazzi with com.android.kotlin.multiplatform.library plugin.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class GreetingTest {
  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun captureGreeting_verifyInitialState() {
    composeTestRule.setContent {
      Greeting()
    }
    // Verify UI is rendered correctly
    composeTestRule.onNodeWithTag("greeting-button").assert(hasText("Hello, Android Multiplatform!"))
    composeTestRule.onRoot().captureRoboImage()
  }

  @Test
  fun captureGreeting_verifyStateAfterClick() {
    composeTestRule.setContent {
      Greeting()
    }
    composeTestRule.onNodeWithTag("greeting-button").performClick()
    // Verify UI state changed after click
    composeTestRule.onNodeWithTag("greeting-button").assert(hasText("Clicked!"))
    composeTestRule.onRoot().captureRoboImage()
  }
}
