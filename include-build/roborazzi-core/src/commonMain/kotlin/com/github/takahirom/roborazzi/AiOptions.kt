package com.github.takahirom.roborazzi

/**
 * If you want to use AI to compare images, you can specify the model and prompt.
 */
data class AiOptions(
  val aiModel: AiModel,
  val aiAssertions: List<AiAssertion> = emptyList(),
  val inputPrompt: (AiOptions) -> String = { aiOptions ->
    buildString {
      aiOptions.aiAssertions.forEachIndexed { index, aiAssertion ->
        appendLine("Assertion ${index + 1}: ${aiAssertion.assertPrompt}\n")
      }
    }
  },
  val template: String = """
Evaluate the following assertion for fulfillment in the new image.
The evaluation should be based on the comparison between the original image on the left and the new image on the right, with differences highlighted in red in the center. Focus on whether the new image fulfills the requirement specified in the user input.

Output:
For each assertion:
A fulfillment percentage from 0 to 100.
A brief explanation of how this percentage was determined.

Assertions: 
INPUT_PROMPT
"""
) {
  interface AiModel {
    data class Gemini(
      val apiKey: String,
      val modelName: String = "gemini-1.5-pro"
    ) : AiModel

    /**
     * You can use this model if you want to use other models.
     */
    interface Manual : AiModel, AiCompareResultFactory
  }

  data class AiAssertion(
    val assertPrompt: String,
    /**
     * If null, the AI result is not validated. But they are still included in the report.
     */
    val requiredFulfillmentPercent: Int
  )
}