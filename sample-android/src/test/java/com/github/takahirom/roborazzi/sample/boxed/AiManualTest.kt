package com.github.takahirom.roborazzi.sample.boxed

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.AiAssertionOptions
import com.github.takahirom.roborazzi.AiAssertionOptions.AiAssertionModel.TargetImages
import com.github.takahirom.roborazzi.AiAssertionResult
import com.github.takahirom.roborazzi.AiAssertionResults
import com.github.takahirom.roborazzi.DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.ROBORAZZI_DEBUG
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roboOutputName
import com.github.takahirom.roborazzi.sample.MainActivity
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
class AiManualTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test(
    expected = AssertionError::class
  )
  fun whenAiReturnError() {
    boxedEnvironment {
      try {
        ROBORAZZI_DEBUG = true
        composeTestRule.onRoot()
          .captureRoboImage(
            roborazziOptions = createOptionsFulfillmentPercent(0)
          )
      }finally {
        File(DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH +File.separator+ roboOutputName() + "_compare.png").delete()
      }
    }
  }

  @Test
  fun whenAiReturnSuccess() {
    boxedEnvironment {
      ROBORAZZI_DEBUG = true
      composeTestRule.onRoot()
        .captureRoboImage(
          roborazziOptions = createOptionsFulfillmentPercent(100)
        )
      File(DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH + File.separator + roboOutputName() + "_compare.png").delete()
    }
  }

  private fun createOptionsFulfillmentPercent(fulfillmentPercent: Int) = RoborazziOptions(
    // Even compare task, it will be failed because of the manual AI model.
    taskType = RoborazziTaskType.Compare,
    compareOptions = RoborazziOptions.CompareOptions(
      aiAssertionOptions = AiAssertionOptions(
        aiAssertionModel = object : AiAssertionOptions.AiAssertionModel {
          override fun assert(
            targetImages: TargetImages,
            aiAssertionOptions: AiAssertionOptions
          ): AiAssertionResults {
            return AiAssertionResults(
              aiAssertionResults = aiAssertionOptions.aiAssertions.map { assertion ->
                AiAssertionResult(
                  assertionPrompt = assertion.assertionPrompt,
                  fulfillmentPercent = fulfillmentPercent,
                  requiredFulfillmentPercent = assertion.requiredFulfillmentPercent,
                  failIfNotFulfilled = assertion.failIfNotFulfilled,
                  explanation = "This is a manual test.",
                )
              }
            )
          }
          override fun assert(
            referenceImageFilePath: String,
            comparisonImageFilePath: String,
            actualImageFilePath: String,
            aiAssertionOptions: AiAssertionOptions
          ): AiAssertionResults {
            return AiAssertionResults(
              aiAssertionResults = aiAssertionOptions.aiAssertions.map { assertion ->
                AiAssertionResult(
                  assertionPrompt = assertion.assertionPrompt,
                  fulfillmentPercent = fulfillmentPercent,
                  requiredFulfillmentPercent = assertion.requiredFulfillmentPercent,
                  failIfNotFulfilled = assertion.failIfNotFulfilled,
                  explanation = "This is a manual test.",
                )
              }
            )
          }
        },
        aiAssertions = listOf(
          AiAssertionOptions.AiAssertion(
            assertionPrompt = "it should have PREVIOUS button",
            requiredFulfillmentPercent = 90,
          ),
        ),
      )
    )
  )
}