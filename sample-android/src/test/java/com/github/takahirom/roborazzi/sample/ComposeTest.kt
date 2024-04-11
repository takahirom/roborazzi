package com.github.takahirom.roborazzi.sample

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.RoborazziTransparentActivity
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<RoborazziTransparentActivity>()

  @get:Rule
  val roborazziRule = RoborazziRule(
    composeRule = composeTestRule,
    captureRoot = composeTestRule.onRoot(),
    options = RoborazziRule.Options(
      captureType = RoborazziRule.CaptureType.LastImage(),
      roborazziOptions = RoborazziOptions(
        recordOptions = RoborazziOptions.RecordOptions(
          applyDeviceCrop = true,
          pixelBitConfig = RoborazziOptions.PixelBitConfig.Argb8888,
        )
      )
    )
  )

  @Test
  fun composable() {
    composeTestRule.setContent {
      SampleComposableFunction()
    }
    (0 until 3).forEach { _ ->
      composeTestRule
        .onNodeWithTag("AddBoxButton")
        .performClick()
    }
  }

  @Test
  fun roundTransparentResizeCompose() {
    composeTestRule.activity.setContent {
      Column(
        modifier = Modifier
            .clip(shape = RoundedCornerShape(16.dp))
            .background(Color.Gray)
            .testTag("SettingsDialog")
            .size(100.dp)
      ) {
        Text("Settings")
        Text("Dark theme")
      }
    }

    composeTestRule
      .onNodeWithTag("SettingsDialog")
      .captureRoboImage(
        roborazziOptions = RoborazziOptions(
          recordOptions = RoborazziOptions.RecordOptions(
            resizeScale = 0.5
          )
        )
      )
  }

  @Test
  @Config(
    sdk = [30],
    qualifiers = "w221dp-h221dp-small-notlong-round-watch-xhdpi-keyshidden-nonav"
  )
  fun wearComposable() {
    composeTestRule.setContent {
      Column(
        Modifier
          .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        val config = LocalConfiguration.current
        Text("${config.screenWidthDp}x${config.screenHeightDp}")
        Text("Round: ${config.isScreenRound}")
      }
    }
    composeTestRule.onNodeWithText("221x221").assertIsDisplayed()
    composeTestRule.onNodeWithText("Round: true").assertIsDisplayed()
    composeTestRule.onNode(isRoot()).captureRoboImage()
  }
}
