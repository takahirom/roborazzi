package com.github.takahirom.roborazzi.sample.boxed

import android.widget.TextView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.DefaultFileNameGenerator
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.ROBORAZZI_DEBUG
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.github.takahirom.roborazzi.sample.MainActivity
import com.github.takahirom.roborazzi.sample.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
  sdk = [30],
  qualifiers = RobolectricDeviceQualifiers.NexusOne
)
class SizeChangeTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun sizeChangeTest() {
    boxedEnvironment {
      ROBORAZZI_DEBUG = true
      val prefix =
        "${roborazziSystemPropertyOutputDirectory()}/${this::class.qualifiedName}.sizeChangeTest"
      val expectedOutput =
        File("$prefix.png")
      val expectedCompareOutput = File("${prefix}_compare.png")
      expectedOutput.delete()
      try {
        onView(ViewMatchers.withId(R.id.textview_first))
          .captureRoboImage(
            roborazziOptions = RoborazziOptions(
              taskType = RoborazziTaskType.Record
            )
          )
        composeTestRule.activity.findViewById<TextView>(R.id.textview_first)
          .text = "Hello, Roborazzi! This is a test for size change."
        DefaultFileNameGenerator.reset()

        onView(ViewMatchers.withId(R.id.textview_first))
          .captureRoboImage(
            roborazziOptions = RoborazziOptions(
              taskType = RoborazziTaskType.Compare
            )
          )
        println(expectedCompareOutput.absolutePath + ":" + expectedCompareOutput.exists())
        assert(expectedCompareOutput.exists())
      } finally {
        expectedCompareOutput.delete()
      }
    }
  }
}
