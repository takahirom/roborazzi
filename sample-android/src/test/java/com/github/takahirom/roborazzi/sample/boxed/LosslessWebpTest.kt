package com.github.takahirom.roborazzi.sample.boxed

import android.widget.TextView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH
import com.github.takahirom.roborazzi.DefaultFileNameGenerator
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.LosslessWebPImageIoFormat
import com.github.takahirom.roborazzi.ROBORAZZI_DEBUG
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
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


@OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
  sdk = [35],
  qualifiers = RobolectricDeviceQualifiers.NexusOne
)
class LosslessWebpTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()
  val recordOptions = RoborazziOptions.RecordOptions(
    imageIoFormat = LosslessWebPImageIoFormat(),
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
  fun whenCompareSameImageWithGradientsTheCompareImageShouldNotBeGenerated() {
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
        val canvasUi = @Composable {
          // Show Canvas transparent gradient
          Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
              color = Color(0xFF000000),
              topLeft = Offset(0f, 0f),
              size = Size(100f, 100f)
            )
            drawRect(
              brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF000000), Color(0x00000000)),
                startY = 0f,
                endY = 100f
              ),
              topLeft = Offset(100f, 0f),
              size = Size(100f, 100f)
            )
            // Color patterns
            drawRect(
              brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFFFF0000), Color(0x00000000)),
                startX = 0f,
                endX = 100f
              ),
              topLeft = Offset(0f, 100f),
              size = Size(100f, 100f)
            )
            drawRect(
              brush = Brush.linearGradient(
                colors = listOf(Color(0xFF00FF00), Color(0x00000000)),
                start = Offset(100f, 100f),
                end = Offset(200f, 200f)
              ),
              topLeft = Offset(100f, 100f),
              size = Size(100f, 100f)
            )
            drawRect(
              brush = Brush.radialGradient(
                colors = listOf(Color(0xFF0000FF), Color(0x00000000)),
                center = Offset(150f, 150f),
                radius = 50f
              ),
              topLeft = Offset(100f, 100f),
              size = Size(100f, 100f)
            )
          }
        }
        captureRoboImage(
          filePath = expectedOutput.absolutePath,
          roborazziOptions = RoborazziOptions(
            taskType = RoborazziTaskType.Record,
            recordOptions = recordOptions
          )
        ) {
          canvasUi()
        }
        DefaultFileNameGenerator.reset()
        captureRoboImage(
          filePath = expectedOutput.absolutePath,
          roborazziOptions = RoborazziOptions(
            taskType = RoborazziTaskType.Compare,
            recordOptions = recordOptions
          ),
        ) {
          canvasUi()
        }
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
