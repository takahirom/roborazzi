package com.github.takahirom.roborazzi.sample

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoboAnimationOptions
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboAnimation
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.NexusOne)
class ComposeAnimationCaptureTest {
  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun captureAnimationWithDelay() {
    composeTestRule.setContent {
      AnimatedBoxContent()
    }
    composeTestRule.onNodeWithTag("root")
      .captureRoboAnimation(
        composeRule = composeTestRule,
        filePath = "${roborazziSystemPropertyOutputDirectory()}/manual_animation_with_delay.gif",
        animationOptions = RoboAnimationOptions(fps = 10),
      ) {
        composeTestRule.onNodeWithTag("toggle").performClick()
        delay(300)
        composeTestRule.onNodeWithTag("toggle").performClick()
        delay(300)
      }
  }

  @Test
  fun captureAnimationSettlesWithoutDelay() {
    composeTestRule.setContent {
      AnimatedBoxContent()
    }
    composeTestRule.onNodeWithTag("root")
      .captureRoboAnimation(
        composeRule = composeTestRule,
        filePath = "${roborazziSystemPropertyOutputDirectory()}/manual_animation_settle.gif",
        animationOptions = RoboAnimationOptions(fps = 10),
      ) {
        // No delay: the animation started by the click is recorded by the settle phase.
        composeTestRule.onNodeWithTag("toggle").performClick()
      }
  }
}

@Composable
private fun AnimatedBoxContent() {
  var expanded by remember { mutableStateOf(false) }
  val boxSize by animateDpAsState(if (expanded) 200.dp else 50.dp, tween(300), label = "size")
  Column(Modifier.testTag("root")) {
    Button(onClick = { expanded = !expanded }, modifier = Modifier.testTag("toggle")) {
      Text("toggle")
    }
    Box(
      Modifier
        .size(boxSize)
        .background(Color.Red)
    )
  }
}
