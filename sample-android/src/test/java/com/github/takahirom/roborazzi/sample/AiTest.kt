package com.github.takahirom.roborazzi.sample

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.AiCompareOptions
import com.github.takahirom.roborazzi.GeminiAiModel
import com.github.takahirom.roborazzi.ROBORAZZI_DEBUG
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.provideRoborazziContext
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
class AiTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val roborazziRule = RoborazziRule(
    options = RoborazziRule.Options(
      roborazziOptions = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(
          aiCompareOptions = AiCompareOptions(
            aiModel = GeminiAiModel(
              apiKey = System.getenv("gemini_api_key") ?: ""
            ),
          )
        )
      )
    )
  )

  @Test
  fun captureWithAi2() {
    ROBORAZZI_DEBUG = true
    if (System.getenv("gemini_api_key") == null) {
      println("Skip the test because gemini_api_key is not set.")
      return
    }
    onView(ViewMatchers.isRoot())
      .captureRoboImage(
        roborazziOptions = provideRoborazziContext().options.addedCompareAiAssertions(
          AiCompareOptions.AiCondition(
            assertPrompt = "it should have PREVIOUS button",
            requiredFulfillmentPercent = 90,
          ),
          AiCompareOptions.AiCondition(
            assertPrompt = "it should show First Fragment",
            requiredFulfillmentPercent = 90,
          )
        )
      )
  }
}