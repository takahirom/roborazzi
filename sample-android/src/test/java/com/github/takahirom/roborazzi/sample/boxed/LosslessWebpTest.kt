package com.github.takahirom.roborazzi.sample.boxed

import android.widget.TextView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH
import com.github.takahirom.roborazzi.DefaultFileNameGenerator
import com.github.takahirom.roborazzi.JvmPlatformRecordOptions
import com.github.takahirom.roborazzi.ROBORAZZI_DEBUG
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.losslessWebPWriter
import com.github.takahirom.roborazzi.nameWithoutExtension
import com.github.takahirom.roborazzi.provideRoborazziContext
import com.github.takahirom.roborazzi.sample.MainActivity
import com.github.takahirom.roborazzi.sample.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File


@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
  sdk = [30],
  qualifiers = RobolectricDeviceQualifiers.NexusOne
)
class LosslessWebpTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()
  val recordOptions = RoborazziOptions.RecordOptions(
    platformRecordOptions = JvmPlatformRecordOptions(
      awtImageWriter = losslessWebPWriter()
    )
  )

  @Test
  fun whenCompareSameImageTheCompareImageShouldNotBeGenerated() {
    boxedEnvironment {
      ROBORAZZI_DEBUG = true
      provideRoborazziContext().setImageExtension("webp")
      val prefix =
        DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH + "/" + DefaultFileNameGenerator.generateFilePath().nameWithoutExtension
      val expectedOutput =
        File("$prefix.webp")
      val expectedCompareOutput = File("${prefix}_compare.webp")
      expectedOutput.delete()
      expectedCompareOutput.delete()
      try {

        onView(ViewMatchers.withId(R.id.textview_first))
          .captureRoboImage(
            filePath = expectedOutput.absolutePath,
            roborazziOptions = RoborazziOptions(
              taskType = RoborazziTaskType.Record,
              recordOptions = recordOptions
            ),
          )
        DefaultFileNameGenerator.reset()

        onView(ViewMatchers.withId(R.id.textview_first))
          .captureRoboImage(
            roborazziOptions = RoborazziOptions(
              taskType = RoborazziTaskType.Compare,
              recordOptions = recordOptions
            )
          )
        assert(expectedOutput.exists())
        assert(!expectedCompareOutput.exists())
      } finally {
        expectedCompareOutput.delete()
      }
    }
  }


  @Test
  fun whenCompareDifferentImageTheCompareImageShouldBeGenerated() {
    boxedEnvironment {
      ROBORAZZI_DEBUG = true
      provideRoborazziContext().setImageExtension("webp")
      val prefix =
        DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH + "/" + DefaultFileNameGenerator.generateFilePath().nameWithoutExtension
      val expectedOutput =
        File("$prefix.webp")
      val expectedCompareOutput = File("${prefix}_compare.webp")
      expectedOutput.delete()
      expectedCompareOutput.delete()
      try {
        onView(ViewMatchers.withId(R.id.textview_first))
          .captureRoboImage(
            filePath = expectedOutput.absolutePath,
            roborazziOptions = RoborazziOptions(
              taskType = RoborazziTaskType.Record,
              recordOptions = recordOptions
            ),
          )
        composeTestRule.activity.findViewById<TextView>(R.id.textview_first)
          .text = "Hello, Roborazzi! This is a test for size change."
        DefaultFileNameGenerator.reset()

        onView(ViewMatchers.withId(R.id.textview_first))
          .captureRoboImage(
            filePath = expectedOutput.absolutePath,
            roborazziOptions = RoborazziOptions(
              taskType = RoborazziTaskType.Compare,
              recordOptions = recordOptions
            )
          )
        assert(expectedOutput.exists())
        assert(expectedCompareOutput.exists())
      } finally {
        expectedCompareOutput.delete()
      }
    }
  }
}
