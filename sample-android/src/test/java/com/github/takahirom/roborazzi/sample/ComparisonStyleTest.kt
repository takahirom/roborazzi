package com.github.takahirom.roborazzi.sample

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
  sdk = [30],
  qualifiers = RobolectricDeviceQualifiers.NexusOne
)
@OptIn(ExperimentalRoborazziApi::class)
class ComparisonStyleTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun diffDefault() {
    ActivityScenario.launch(MainActivity::class.java)

    onView(ViewMatchers.isRoot())
      .captureRoboImage()
  }

  @Test
  fun simple() {
    ActivityScenario.launch(MainActivity::class.java)

    onView(ViewMatchers.isRoot())
      .captureRoboImage(
        roborazziOptions = RoborazziOptions(
          compareOptions = RoborazziOptions.CompareOptions(
            comparisonStyle = RoborazziOptions.CompareOptions.ComparisonStyle.Simple
          )
        )
      )
  }

  @Test
  fun diffNoLabel() {
    ActivityScenario.launch(MainActivity::class.java)

    onView(ViewMatchers.isRoot())
      .captureRoboImage(
        roborazziOptions = RoborazziOptions(
          compareOptions = RoborazziOptions.CompareOptions(
            comparisonStyle = RoborazziOptions.CompareOptions.ComparisonStyle.Grid(
              hasLabel = false
            )
          )
        )
      )
  }

  @Test
  fun diffNoSmallLineSpace() {
    ActivityScenario.launch(MainActivity::class.java)

    onView(ViewMatchers.isRoot())
      .captureRoboImage(
        roborazziOptions = RoborazziOptions(
          compareOptions = RoborazziOptions.CompareOptions(
            comparisonStyle = RoborazziOptions.CompareOptions.ComparisonStyle.Grid(
              smallLineSpaceDp = null
            )
          )
        )
      )
  }

  @Test
  fun diffNoBigLineSpace() {
    ActivityScenario.launch(MainActivity::class.java)

    onView(ViewMatchers.isRoot())
      .captureRoboImage(
        roborazziOptions = RoborazziOptions(
          compareOptions = RoborazziOptions.CompareOptions(
            comparisonStyle = RoborazziOptions.CompareOptions.ComparisonStyle.Grid(
              bigLineSpaceDp = null
            )
          )
        )
      )
  }
}
