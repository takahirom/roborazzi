package com.github.takahirom.roborazzi

/**
 * If you want to use AI to compare images, you can specify the model and prompt.
 */
data class AiOptions(
  val model: Model,
  val prompt: String,
  val template: String = """
Evaluate the following user input for fulfillment in the new image: "PROMPT".
The evaluation should be based on the comparison between the original image on the left and the new image on the right, with differences highlighted in red in the center. Focus on whether the new image fulfills the requirement specified in the user input.

Output:
A fulfillment percentage from 0 to 100.
A brief explanation of how this percentage was determined.

User Input: "PROMPT" 
""",
  /**
   * If null, the AI result is not validated. But they are still included in the report.
   */
  val requiredFulfillmentPercent: Int? = null,
) {
  sealed interface Model {
    data class Gemini(
      val apiKey: String,
      val modelName: String = "gemini-1.5-pro"
    ) : Model
  }
}