# Experimental AI-Powered Image Assertion

Roborazzi supports AI-powered image assertion. 
AI-powered image assertion is an experimental feature. Screenshot tests are a great way to verify the UI of your app, but verifying the content of the image is challenging because it is a manual process. Roborazzi can help you automate this process by using AI-powered image assertion.

There are two new library modules: `io.github.takahirom.roborazzi:roborazzi-ai-gemini` and `io.github.takahirom.roborazzi:roborazzi-ai-openai` for AI-powered image assertion.

`roborazzi-ai-gemini` uses [Gemini](https://gemini.google.com/), and `roborazzi-ai-openai` uses the [OpenAI API](https://platform.openai.com/).

```kotlin
...
@get:Rule
val composeTestRule = createAndroidComposeRule<MainActivity>()

@get:Rule
val roborazziRule = RoborazziRule(
  options = RoborazziRule.Options(
    roborazziOptions = RoborazziOptions(
      compareOptions = RoborazziOptions.CompareOptions(
        aiAssertionOptions = AiAssertionOptions(
          aiAssertionModel = GeminiAiAssertionModel(
            apiKey = System.getenv("gemini_api_key") ?: ""
          ),
        )
      )
    )
  )
)

@Test
fun captureWithAi() {
  ROBORAZZI_DEBUG = true
  onView(ViewMatchers.isRoot())
    .captureRoboImage(
      roborazziOptions = provideRoborazziContext().options.addedAiAssertions(
        AiAssertionOptions.AiAssertion(
          assertPrompt = "it should have PREVIOUS button",
          requiredFulfillmentPercent = 90,
        ),
        AiAssertionOptions.AiAssertion(
          assertPrompt = "it should show First Fragment",
          requiredFulfillmentPercent = 90,
        )
      )
    )
}
```

## Behavior of AI-Powered Image Assertion

AI-Powered Image Assertion runs only when the images are different. If the images are the same, AI-Powered Image Assertion is skipped.  
This is because AI-Powered Image Assertion can be slow and expensive.

## Manual Image Assertion

You can use manual image assertion with Roborazzi. This allows you to utilize local LLMs or other LLMs. Manual Image Assertion doesn't require adding any dependencies other than Roborazzi itself.

You must provide the `AiAssertionModel` to `RoborazziOptions` to use manual image assertion.

```kotlin
interface AiAssertionModel {
  fun assert(
    referenceImageFilePath: String,
    comparisonImageFilePath: String,
    actualImageFilePath: String,
    aiAssertionOptions: AiAssertionOptions
  ): AiAssertionResults
}
```

```kotlin
compareOptions = RoborazziOptions.CompareOptions(
  aiAssertionOptions = AiAssertionOptions(
    aiAssertionModel = object : AiAssertionOptions.AiAssertionModel {
      override fun assert(
        comparisonImageFilePath: String,
        aiAssertionOptions: AiAssertionOptions
      ): AiAssertionResults {
        // You can use any LLMs here to create AiAssertionResults
        return AiAssertionResults(
          aiAssertionResults = aiAssertionOptions.aiAssertions.map { assertion ->
            AiAssertionResult(
              assertPrompt = assertion.assertPrompt,
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
        assertPrompt = "it should have PREVIOUS button",
        requiredFulfillmentPercent = 90,
      ),
    ),
  )
)
          ...
```
