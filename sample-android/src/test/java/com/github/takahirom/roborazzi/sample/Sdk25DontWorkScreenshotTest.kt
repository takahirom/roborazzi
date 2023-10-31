package com.github.takahirom.roborazzi.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Sdk25DontWorkScreenshotTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun sdk25DontWorkScreenshot() {
    composeTestRule.setContent {
      Box(
        Modifier
          .size(100.dp)
          .testTag("box")
          .background(Color.Red),
      ) {
        Box(
          Modifier
            .size(50.dp)
            .testTag("box2")
            .background(Color.Blue),
        )
      }
    }
    composeTestRule.onRoot().captureRoboImage()
  }
}
