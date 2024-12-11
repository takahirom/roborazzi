package com.github.takahirom.roborazzi.sample

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dropbox.differ.ImageComparator
import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.Dump
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoboComponent
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziComposeActivityScenarioOption
import com.github.takahirom.roborazzi.RoborazziComposeComposableOption
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboAllImage
import com.github.takahirom.roborazzi.captureRoboGif
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureRoboLastImage
import com.github.takahirom.roborazzi.fontScale
import com.github.takahirom.roborazzi.localInspectionMode
import com.github.takahirom.roborazzi.roboOutputName
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.github.takahirom.roborazzi.withComposeTestTag
import com.github.takahirom.roborazzi.withViewId
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
class ManualTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  @Config(qualifiers = "+land")
  fun captureScreenLevelImageWithEspresso() {
    onView(ViewMatchers.isRoot())
      .captureRoboImage()
  }

  @Test
  @Config(qualifiers = "+land")
  fun captureScreenLevelImageWithEspressoAndScaleOptions() {
    onView(ViewMatchers.isRoot())
      .captureRoboImage(
        roborazziOptions = RoborazziOptions(
          compareOptions = RoborazziOptions.CompareOptions(
            imageComparator = SimpleImageComparator()
          ),
          recordOptions = RoborazziOptions.RecordOptions(
            resizeScale = 0.5,
          )
        )
      )
  }

  @Test
  @Config(qualifiers = "+night")
  fun captureScreenLevelNightWithEspresso() {
    onView(ViewMatchers.isRoot())
      .captureRoboImage()
  }

  @Test
  @Config(qualifiers = "+ja")
  fun captureScreenLevelJapaneseWithEspresso() {
    onView(ViewMatchers.isRoot())
      .captureRoboImage()
  }

  @Test
  @Config(qualifiers = RobolectricDeviceQualifiers.MediumTablet)
  fun captureScreenLevelTabletWithEspresso() {
    onView(ViewMatchers.isRoot())
      .captureRoboImage()
  }

  @OptIn(ExperimentalRoborazziApi::class)
  @Test
  @Config(qualifiers = RobolectricDeviceQualifiers.MediumTablet)
  fun captureScreenWithMetadata() {
    onView(ViewMatchers.isRoot())
      .captureRoboImage(
        roborazziOptions = RoborazziOptions(
          contextData = mapOf(
            "context_data_key" to "context_data_value"
          )
        )
      )
  }

  @Test
  fun captureComposeImage() {
    composeTestRule.onNodeWithTag("AddBoxButton")
      .onParent()
      .captureRoboImage()
  }

  @OptIn(ExperimentalRoborazziApi::class)
  @Test
  fun captureSmallComponentImage() {
    onView(withId(R.id.button_first))
      .captureRoboImage(
        filePath = "${roborazziSystemPropertyOutputDirectory()}/manual_small_view_button.png",
        roborazziOptions = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(0.5))
      )
  }

  @Test
  fun moveToNextPageWithEspresso() {
    onView(withId(R.id.button_first))
      .perform(click())

    onView(ViewMatchers.isRoot())
      .captureRoboImage()
  }

  @OptIn(ExperimentalRoborazziApi::class)
  @Test
  fun captureViewOnWindowImage() {
    composeTestRule.activity.findViewById<View>(R.id.button_first)
      .captureRoboImage("${roborazziSystemPropertyOutputDirectory()}/manual_view_on_window.png")
  }

  @OptIn(ExperimentalRoborazziApi::class)
  @Test
  fun captureViewNotOnWindowImage() {
    TextView(composeTestRule.activity).apply {
      text = "Hello View!"
      setTextColor(Color.RED)
    }.captureRoboImage("${roborazziSystemPropertyOutputDirectory()}/manual_view_without_window.png")
  }

  @OptIn(ExperimentalRoborazziApi::class)
  @Test
  fun captureComposeLambdaImage() {
    captureRoboImage("${roborazziSystemPropertyOutputDirectory()}/manual_compose.png") {
      Text("Hello Compose!")
    }
  }

  @OptIn(ExperimentalRoborazziApi::class)
  @Test
  fun captureComposeLambdaImageWithRoborazziComposeOptions() {
    captureRoboImage(
      "${roborazziSystemPropertyOutputDirectory()}/manual_compose_with_compose_options.png",
      roborazziComposeOptions = RoborazziComposeOptions {
        // We have several options to configure the test environment.
        fontScale(2f)
        /* The default value is false, but we can set it to true,
        if you want to use the logic for Preview in composable functions. */
        localInspectionMode(false)
        // We can also configure the activity scenario and the composable content.
        addOption(
          object : RoborazziComposeComposableOption,
            RoborazziComposeActivityScenarioOption {
            override fun configureWithActivityScenario(scenario: ActivityScenario<out Activity>) {
              scenario.onActivity {
                it.window.decorView.setBackgroundColor(Color.BLUE)
              }
            }

            override fun configureWithComposable(content: @Composable () -> Unit): @Composable () -> Unit {
              return {
                Box(Modifier
                  .padding(10.dp)
                  .background(color = androidx.compose.ui.graphics.Color.Red)
                  .padding(10.dp)
                ) {
                  content()
                }
              }
            }
          }
        )
      },
    ) {
      Text("Hello Compose!")
    }
  }

  @OptIn(ExperimentalRoborazziApi::class)
  @Test
  fun captureBitmapImage() {
    createBitmap(100, 100, Bitmap.Config.ARGB_8888)
      .apply {
        applyCanvas {
          drawColor(Color.YELLOW)
        }
      }
      .captureRoboImage("${roborazziSystemPropertyOutputDirectory()}/manual_bitmap.png")
  }

  @OptIn(ExperimentalRoborazziApi::class)
  @Test
  fun captureRoboImageSampleWithQuery() {
    val filePath =
      "${roborazziSystemPropertyOutputDirectory()}/manual_view_first_screen_with_query_view.png"
    onView(ViewMatchers.isRoot())
      .captureRoboImage(
        filePath = filePath,
        roborazziOptions = RoborazziOptions(
          captureType = RoborazziOptions.CaptureType.Dump(query = withViewId(R.id.textview_first))
        )
      )

    composeTestRule.onNodeWithTag("AddBoxButton")
      .performClick()

    composeTestRule.onNodeWithTag("AddBoxButton")
      .performClick()
    composeTestRule.waitForIdle()

    onView(ViewMatchers.isRoot())
      .captureRoboImage(
        filePath = "${roborazziSystemPropertyOutputDirectory()}/manual_view_first_screen_with_query_compose.png",
        roborazziOptions = RoborazziOptions(
          captureType = RoborazziOptions.CaptureType.Dump(
            query = withComposeTestTag("child:0"),
          ),
          compareOptions = RoborazziOptions.CompareOptions { result: ImageComparator.ComparisonResult -> result.pixelDifferences < 1 }
        )
      )

    onView(ViewMatchers.isRoot())
      .captureRoboImage(
        filePath = "${roborazziSystemPropertyOutputDirectory()}/manual_view_first_screen_with_query_compose_custom.png",
        roborazziOptions = RoborazziOptions(
          captureType = RoborazziOptions.CaptureType.Dump(
            query = { roboComponent ->
              when (roboComponent) {
                is RoboComponent.Screen -> false
                is RoboComponent.Compose -> roboComponent.testTag?.startsWith("child") == true
                is RoboComponent.View -> false
              }
            })
        )
      )


    onView(ViewMatchers.isRoot())
      .captureRoboImage(
        filePath = "${roborazziSystemPropertyOutputDirectory()}/manual_view_a11y_dump.png",
        roborazziOptions = RoborazziOptions(
          captureType = RoborazziOptions.CaptureType.Dump(
            explanation = Dump.AccessibilityExplanation,
          )
        )
      )
  }


  @OptIn(ExperimentalRoborazziApi::class)
  @Test
  fun captureRoboGifSample() {
    onView(ViewMatchers.isRoot())
      .captureRoboGif("${roborazziSystemPropertyOutputDirectory()}/manual_gif.gif") {
        // move to next page
        onView(withId(R.id.button_first))
          .perform(click())
        // back
        pressBack()
      }
    onView(ViewMatchers.isRoot())
      .captureRoboLastImage("${roborazziSystemPropertyOutputDirectory()}/manual_last.png") {
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
      .captureRoboAllImage({ File("${roborazziSystemPropertyOutputDirectory()}/manual_all_$it.png") }) {
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

  @OptIn(ExperimentalRoborazziApi::class)
  @Test
  @Config(
    qualifiers = "w150dp-h200dp",
  )
  fun captureRoboGifSampleCompose() {
    composeTestRule.onNodeWithTag("MyColumn")
      .captureRoboGif(
        composeTestRule,
        "${roborazziSystemPropertyOutputDirectory()}/manual_captureRoboGifSampleCompose.gif"
      ) {
        composeTestRule.onNodeWithTag("SubBoxButton")
          .performClick()
        composeTestRule.onNodeWithTag("AddBoxButton")
          .performClick()
        composeTestRule.onNodeWithTag("AddBoxButton")
          .performClick()
      }
  }

  @OptIn(ExperimentalRoborazziApi::class)
  @Test
  fun roboOutputNameTest() {
    assert(roboOutputName() == "com.github.takahirom.roborazzi.sample.ManualTest.roboOutputNameTest")
  }
}
