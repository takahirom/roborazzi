package com.github.takahirom.roborazzi

/**
 * If you want to use AI to compare images, you can specify the model and prompt.
 */
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
        appendLine("Assertion ${index + 1}: ${aiAssertion.assertPrompt}\n")
      }
    }
  },
) {
  interface AiAssertionModel {
    fun assert(
      comparisonImageFilePath: String,
      aiAssertionOptions: AiAssertionOptions
    ): AiAssertionResults
  }

  data class AiAssertion(
    val assertPrompt: String,
    val failIfNotFulfilled: Boolean = true,
    /**
     * If null, the AI result is not validated. But they are still included in the report.
     */
    val requiredFulfillmentPercent: Int? = 80
  )
}