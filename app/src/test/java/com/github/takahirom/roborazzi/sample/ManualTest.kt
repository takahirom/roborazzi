package com.github.takahirom.roborazzi.sample

import android.graphics.Bitmap
import android.view.View
import android.widget.TextView
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dropbox.differ.ImageComparator
import com.github.takahirom.roborazzi.DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH
import com.github.takahirom.roborazzi.RoboComponent
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboAllImage
import com.github.takahirom.roborazzi.captureRoboGif
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureRoboLastImage
import com.github.takahirom.roborazzi.withComposeTestTag
import com.github.takahirom.roborazzi.withViewId
import java.io.File
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
class ManualTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test @Config(qualifiers = "+land")
  fun captureRoboImageSample() {
    // screen level image
    onView(ViewMatchers.isRoot())
      .captureRoboImage()

    // compose image
    composeTestRule.onNodeWithTag("MyComposeButton")
      .onParent()
      .captureRoboImage()

    // small component image
    onView(withId(R.id.button_first))
      .captureRoboImage(
        filePath = "$DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/manual_small_view_button.png",
        roborazziOptions = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(0.5))
      )

    // move to next page with Espresso
    onView(withId(R.id.button_first))
      .perform(click())

    onView(ViewMatchers.isRoot())
      .captureRoboImage()

    // View on window
    composeTestRule.activity.findViewById<View>(R.id.button_second)
      .captureRoboImage("$DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/manual_view_on_window.png")

    // View not on window
    TextView(composeTestRule.activity).apply {
      text = "Hello View!"
      setTextColor(android.graphics.Color.RED)
    }.captureRoboImage("$DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/manual_view_without_window.png")

    // Compose lambda
    captureRoboImage("$DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/manual_compose.png") {
      Text("Hello Compose!")
    }

    // Bitmap
    createBitmap(100, 100, Bitmap.Config.ARGB_8888)
      .apply {
        applyCanvas {
          drawColor(android.graphics.Color.YELLOW)
        }
      }
      .captureRoboImage("$DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/manual_bitmap.png")
  }

  @Test
  fun captureRoboImageSampleWithQuery() {
    val filePath = "$DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/manual_view_first_screen_with_query_view.png"
    onView(ViewMatchers.isRoot())
      .captureRoboImage(
        filePath = filePath,
        roborazziOptions = RoborazziOptions(
          captureType = RoborazziOptions.CaptureType.Dump(query = withViewId(R.id.textview_first))
        )
      )

    composeTestRule.onNodeWithTag("MyComposeButton")
      .performClick()

    composeTestRule.onNodeWithTag("MyComposeButton")
      .performClick()
    composeTestRule.waitForIdle()

    onView(ViewMatchers.isRoot())
      .captureRoboImage(
        filePath = "$DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/manual_view_first_screen_with_query_compose.png",
        roborazziOptions = RoborazziOptions(
          captureType = RoborazziOptions.CaptureType.Dump(
            query = withComposeTestTag("child:0"),
          ),
          compareOptions = RoborazziOptions.CompareOptions { result: ImageComparator.ComparisonResult -> result.pixelDifferences < 1 }
        )
      )

    onView(ViewMatchers.isRoot())
      .captureRoboImage(
        filePath = "$DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/manual_view_first_screen_with_query_compose_custom.png",
        roborazziOptions = RoborazziOptions(
          captureType = RoborazziOptions.CaptureType.Dump(
            query = { roboComponent ->
              when (roboComponent) {
                is RoboComponent.Compose -> roboComponent.testTag?.startsWith("child") == true
                is RoboComponent.View -> false
              }
            })
        )
      )
  }


  @Test
  fun captureRoboGifSample() {
    onView(ViewMatchers.isRoot())
      .captureRoboGif("$DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/manual_gif.gif") {
        // move to next page
        onView(withId(R.id.button_first))
          .perform(click())
        // back
        pressBack()
      }
    onView(ViewMatchers.isRoot())
      .captureRoboLastImage("$DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/manual_last.png") {
        // move to next page
        onView(withId(R.id.button_first))
          .perform(click())
        // back
        pressBack()
        // move to next page
        onView(withId(R.id.button_first))
          .perform(click())
      }

    onView(ViewMatchers.isRoot())
      .captureRoboAllImage({ File("$DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/manual_all_$it.png") }) {
        // back
        pressBack()
        // move to next page
        onView(withId(R.id.button_first))
          .perform(click())
        // back
        pressBack()
        // move to next page
        onView(withId(R.id.button_first))
          .perform(click())
      }
  }

  @Test
  @Config(
    qualifiers = "w150dp-h200dp",
  )
  fun captureRoboGifSampleCompose() {
    composeTestRule.onRoot(false)
      .captureRoboGif(
        composeTestRule,
        "$DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/manual_captureRoboGifSampleCompose.gif"
      ) {
        composeTestRule.onNodeWithTag("MyComposeButton")
          .performClick()
        composeTestRule.onNodeWithTag("MyComposeButton")
          .performClick()
        composeTestRule.onNodeWithTag("MyComposeButton")
          .performClick()
      }
  }
}
