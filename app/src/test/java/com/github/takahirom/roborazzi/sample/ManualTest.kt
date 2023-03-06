package com.github.takahirom.roborazzi.sample

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.CaptureOptions
import com.github.takahirom.roborazzi.DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH
import com.github.takahirom.roborazzi.RoboComponent
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

private const val PATH_AND_PREFIX_FOR_FILE: String = "$DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/manual"

@Config(qualifiers = "xlarge-land")
@RunWith(AndroidJUnit4::class)
class ManualTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun captureRoboImageSample() {
    // screen level image
    onView(ViewMatchers.isRoot())
      .captureRoboImage("${PATH_AND_PREFIX_FOR_FILE}_view_first_screen.png")

    // compose image
    composeTestRule.onNodeWithTag("MyComposeButton")
      .onParent()
      .captureRoboImage("${PATH_AND_PREFIX_FOR_FILE}_small_compose.png")

    // small component image
    onView(withId(R.id.button_first))
      .captureRoboImage("${PATH_AND_PREFIX_FOR_FILE}_small_view_button.png")

    // move to next page
    onView(withId(R.id.button_first))
      .perform(click())

    onView(ViewMatchers.isRoot())
      .captureRoboImage("${PATH_AND_PREFIX_FOR_FILE}_second_screen.png")
  }

  @Test
  fun captureRoboImageSampleWithQuery() {
    onView(ViewMatchers.isRoot())
      .captureRoboImage(
        filePath = "${PATH_AND_PREFIX_FOR_FILE}_view_first_screen_with_query_view.png",
        captureOptions = CaptureOptions(query = withViewId(R.id.textview_first))
      )

    composeTestRule.onNodeWithTag("MyComposeButton")
      .performClick()

    composeTestRule.onNodeWithTag("MyComposeButton")
      .performClick()
    composeTestRule.waitForIdle()

    onView(ViewMatchers.isRoot())
      .captureRoboImage(
        filePath = "${PATH_AND_PREFIX_FOR_FILE}_view_first_screen_with_query_compose.png",
        captureOptions = CaptureOptions(query = withComposeTestTag("child:0"))
      )

    onView(ViewMatchers.isRoot())
      .captureRoboImage(
        filePath = "${PATH_AND_PREFIX_FOR_FILE}_view_first_screen_with_query_compose_custom.png",
        captureOptions = CaptureOptions(query = { roboComponent ->
          when (roboComponent) {
            is RoboComponent.Compose -> roboComponent.testTag?.startsWith("child") == true
            is RoboComponent.View -> false
          }
        })
      )
  }


  @Test
  fun captureRoboGifSample() {
    onView(ViewMatchers.isRoot())
      .captureRoboGif("${PATH_AND_PREFIX_FOR_FILE}_gif.gif") {
        // move to next page
        onView(withId(R.id.button_first))
          .perform(click())
        // back
        pressBack()
      }
    onView(ViewMatchers.isRoot())
      .captureRoboLastImage("${PATH_AND_PREFIX_FOR_FILE}_last.png") {
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
      .captureRoboAllImage({ File("${PATH_AND_PREFIX_FOR_FILE}_all_$it.png") }) {
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
  fun captureRoboGifSampleCompose() {
    composeTestRule.onRoot(false)
      .captureRoboGif(
        composeTestRule,
        "${PATH_AND_PREFIX_FOR_FILE}_captureRoboGifSampleCompose.gif"
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
