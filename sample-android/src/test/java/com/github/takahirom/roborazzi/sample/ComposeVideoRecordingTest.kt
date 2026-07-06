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
import com.github.takahirom.roborazzi.RoboVideoOptions
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.recordRoboVideo
import com.github.takahirom.roborazzi.recordScreenRoboVideo
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
class ComposeVideoRecordingTest {
  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun recordVideoWithDelay() {
    composeTestRule.setContent {
      AnimatedBoxContent()
    }
    composeTestRule.onNodeWithTag("root")
      .recordRoboVideo(
        composeRule = composeTestRule,
        filePath = "${roborazziSystemPropertyOutputDirectory()}/manual_video_with_delay.gif",
        videoOptions = RoboVideoOptions(fps = 10),
      ) {
        composeTestRule.onNodeWithTag("toggle").performClick()
        delay(300)
        composeTestRule.onNodeWithTag("toggle").performClick()
        delay(300)
      }
  }

  @Test
  fun recordVideoAsApng() {
    composeTestRule.setContent {
      AnimatedBoxContent()
    }
    composeTestRule.onNodeWithTag("root")
      .recordRoboVideo(
        // A .png extension produces a lossless, full-color APNG instead of a GIF.
        composeRule = composeTestRule,
        filePath = "${roborazziSystemPropertyOutputDirectory()}/manual_video_apng.png",
        videoOptions = RoboVideoOptions(fps = 10),
      ) {
        composeTestRule.onNodeWithTag("toggle").performClick()
        delay(300)
        composeTestRule.onNodeWithTag("toggle").performClick()
        delay(300)
      }
  }

  @Test
  fun recordVideoScreen() {
    composeTestRule.setContent {
      AnimatedBoxContent()
    }
    // Screen-level recording keeps every frame at the device size even as the box grows/shrinks,
    // unlike the node-scoped variant whose frame dimensions track the animating content.
    recordScreenRoboVideo(
      composeRule = composeTestRule,
      filePath = "${roborazziSystemPropertyOutputDirectory()}/manual_video_screen.gif",
      videoOptions = RoboVideoOptions(fps = 10),
    ) {
      composeTestRule.onNodeWithTag("toggle").performClick()
      delay(300)
      composeTestRule.onNodeWithTag("toggle").performClick()
      delay(300)
    }
  }

  @Test
  fun recordVideoSettlesWithoutDelay() {
    composeTestRule.setContent {
      AnimatedBoxContent()
    }
    composeTestRule.onNodeWithTag("root")
      .recordRoboVideo(
        composeRule = composeTestRule,
        filePath = "${roborazziSystemPropertyOutputDirectory()}/manual_video_settle.gif",
        videoOptions = RoboVideoOptions(fps = 10),
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
