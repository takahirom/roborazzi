# Experimental AI-Powered Image Assertion

Roborazzi supports AI-powered image assertion.
AI-powered image assertion is an experimental feature. Screenshot tests are a great way to verify your app's UI, but verifying the content of the images can be a tedious and time-consuming task. This manual effort reduces scalability. Roborazzi can help automate this process through AI-powered image assertion, making it more efficient and scalable.

There are two new library modules: `io.github.takahirom.roborazzi:roborazzi-ai-gemini` and `io.github.takahirom.roborazzi:roborazzi-ai-openai` for AI-powered image assertion.

`roborazzi-ai-gemini` leverages [Gemini](https://gemini.google.com/) and [generative-ai-kmp](https://github.com/PatilShreyas/generative-ai-kmp), while `roborazzi-ai-openai` utilizes the [OpenAI API](https://platform.openai.com/) through raw HTTP API calls implemented with Ktor and KotlinX Serialization

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
            // DO NOT HARDCODE your API key in your code.
            // This is an example passing API Key through unitTests.all{ environment(key, value) }
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
