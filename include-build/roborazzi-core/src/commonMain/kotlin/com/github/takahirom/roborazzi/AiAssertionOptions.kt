package com.github.takahirom.roborazzi

/**
 * If you want to use AI to compare images, you can specify the model and prompt.
 */
@ExperimentalRoborazziApi
data class AiAssertionOptions(
  val aiAssertionModel: AiAssertionModel,
  val aiAssertions: List<AiAssertion> = emptyList(),
  val systemPrompt: String = """Evaluate the following assertion for fulfillment in the new image.
The evaluation should be based on the comparison between the original image on the left and the new image on the right, with differences highlighted in red in the center. Focus on whether the new image fulfills the requirement specified in the user input.

Output:
For each assertion:
A fulfillment percentage from 0 to 100.
A brief explanation of how this percentage was determined.""",
  val promptTemplate: String = """Assertions:
INPUT_PROMPT
""",
  val inputPrompt: (AiAssertionOptions) -> String = { aiOptions ->
    buildString {
      aiOptions.aiAssertions.forEachIndexed { index, aiAssertion ->
        appendLine("Assertion ${index + 1}: ${aiAssertion.assertionPrompt}\n")
      }
    }
  },
) {
  @ExperimentalRoborazziApi
  interface AiAssertionModel {
    fun assert(
      referenceImageFilePath: String,
      comparisonImageFilePath: String,
      actualImageFilePath: String,
      aiAssertionOptions: AiAssertionOptions
    ): AiAssertionResults
    companion object {
      const val DefaultMaxOutputTokens = 300
      const val DefaultTemperature = 0.4F
    }
  }

  data class AiAssertion(
    val assertionPrompt: String,
    val failIfNotFulfilled: Boolean = true,
    /**
     * If null, the AI result is not validated. But the fulfillment_percent are still included in the report.
     */
    val requiredFulfillmentPercent: Int? = 80
  )
}