package com.github.takahirom.roborazzi.sample

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @get:Rule
  val roborazziRule = RoborazziRule(composeTestRule, composeTestRule.onRoot())

  @Test
  fun composable() {
    composeTestRule.setContent {
      SampleComposableFunction()
    }
    (0 until 5).forEach { _ ->
      composeTestRule
        .onNodeWithTag("MyComposeRoot")
        .performClick()
    }
  }
}
