package com.github.takahirom.roborazzi.sample.boxed

import android.graphics.BitmapFactory
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.DefaultFileNameGenerator
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.ResizeScaleCalculator
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.github.takahirom.roborazzi.sample.MainActivity
import com.github.takahirom.roborazzi.sample.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class ResizeScaleCalculatorTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun goldenIsRecordedAndComparedWithTheCalculatedScale() {
    boxedEnvironment {
      val prefix =
        "${roborazziSystemPropertyOutputDirectory()}/${this::class.qualifiedName}.goldenIsRecordedAndComparedWithTheCalculatedScale"
      val golden = File("$prefix.png")
      val compareOutput = File("${prefix}_compare.png")
      golden.delete()
      compareOutput.delete()
      val requestedSizes = mutableListOf<ResizeScaleCalculator.Size>()
      val recordOptions = RoborazziOptions.RecordOptions(
        resizeScaleCalculator = { size ->
          requestedSizes += size
          0.5
        }
      )
      try {
        onView(ViewMatchers.withId(R.id.textview_first))
          .captureRoboImage(
            roborazziOptions = RoborazziOptions(
              taskType = RoborazziTaskType.Record,
              recordOptions = recordOptions,
            )
          )
        val capturedSize = requestedSizes.single()
        val image = BitmapFactory.decodeFile(golden.absolutePath)
        assertEquals((capturedSize.width * 0.5).toInt(), image.width)
        assertEquals((capturedSize.height * 0.5).toInt(), image.height)

        DefaultFileNameGenerator.reset()
        onView(ViewMatchers.withId(R.id.textview_first))
          .captureRoboImage(
            roborazziOptions = RoborazziOptions(
              taskType = RoborazziTaskType.Compare,
              recordOptions = recordOptions,
            )
          )

        assertFalse("An unchanged image must not produce a compare image", compareOutput.exists())
      } finally {
        golden.delete()
        compareOutput.delete()
      }
    }
  }
}
