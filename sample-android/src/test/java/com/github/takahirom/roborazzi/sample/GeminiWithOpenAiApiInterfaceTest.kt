package com.github.takahirom.roborazzi.sample

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.AiAssertionOptions
import com.github.takahirom.roborazzi.DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.OpenAiAiAssertionModel
import com.github.takahirom.roborazzi.ROBORAZZI_DEBUG
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.provideRoborazziContext
import com.github.takahirom.roborazzi.roboOutputName
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
  sdk = [30],
  qualifiers = RobolectricDeviceQualifiers.NexusOne
)
class GeminiWithOpenAiApiInterfaceTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val roborazziRule = RoborazziRule(
    options = RoborazziRule.Options(
      roborazziOptions = RoborazziOptions(
        taskType =  RoborazziTaskType.Compare,
        compareOptions = RoborazziOptions.CompareOptions(
          aiAssertionOptions = AiAssertionOptions(
            aiAssertionModel = OpenAiAiAssertionModel(
//              loggingEnabled = true,
              baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
              apiKey = System.getenv("gemini_api_key").orEmpty(),
              apiType = OpenAiAiAssertionModel.ApiType.Gemini,
              modelName = "gemini-1.5-flash",
              seed = null,
            ),
          )
        )
      )
    )
  )

  @Test
  fun captureWithAi() {
    ROBORAZZI_DEBUG = true
    if (System.getenv("gemini_api_key") == null) {
      println("Skip the test because openai_api_key is not set.")
      return
    }
    File(DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH + File.separator + roboOutputName() + ".png").delete()
    onView(ViewMatchers.isRoot())
      .captureRoboImage(
        roborazziOptions = provideRoborazziContext().options.addedAiAssertions(
          AiAssertionOptions.AiAssertion(
            assertionPrompt = "it should have PREVIOUS button",
            requiredFulfillmentPercent = 90,
          ),
          AiAssertionOptions.AiAssertion(
            assertionPrompt = "it should show First Fragment",
            requiredFulfillmentPercent = 90,
          )
        )
      )
    File(DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH + File.separator + roboOutputName() + "_compare.png").delete()
  }
}