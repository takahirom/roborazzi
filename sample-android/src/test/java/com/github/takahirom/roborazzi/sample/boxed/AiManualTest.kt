package com.github.takahirom.roborazzi.sample.boxed

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.AiCompareOptions
import com.github.takahirom.roborazzi.AiComparisonResult
import com.github.takahirom.roborazzi.AiConditionResult
import com.github.takahirom.roborazzi.ROBORAZZI_DEBUG
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.sample.MainActivity
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
class AiManualTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test(
    expected = AssertionError::class
  )
  fun whenAiReturnError() {
    boxedEnvironment {
      ROBORAZZI_DEBUG = true
      composeTestRule.onRoot()
        .captureRoboImage(
          roborazziOptions = createOptionsFulfillmentPercent(0)
        )
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
    }
  }

  private fun createOptionsFulfillmentPercent(fulfillmentPercent: Int) = RoborazziOptions(
    // Even compare task, it will be failed because of the manual AI model.
    taskType = RoborazziTaskType.Compare,
    compareOptions = RoborazziOptions.CompareOptions(
      aiCompareOptions = AiCompareOptions(
        aiModel = object : AiCompareOptions.AiModel.Manual {
          override fun invoke(
            comparisonImageFilePath: String,
            aiCompareOptions: AiCompareOptions
          ): AiComparisonResult {
            return AiComparisonResult(
              aiConditionResults = aiCompareOptions.aiConditions.map {
                AiConditionResult(
                  assertPrompt = it.assertPrompt,
                  fulfillmentPercent = fulfillmentPercent,
                  requiredFulfillmentPercent = it.requiredFulfillmentPercent,
                  explanation = "This is a manual test.",
                )
              }
            )
          }
        },
        aiConditions = listOf(
          AiCompareOptions.AiCondition(
            assertPrompt = "it should have PREVIOUS button",
            requiredFulfillmentPercent = 90,
          ),
        ),
      )
    )
  )
}