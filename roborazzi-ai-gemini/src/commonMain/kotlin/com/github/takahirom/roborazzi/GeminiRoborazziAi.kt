package com.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.AiCompareOptions.AiModel
import dev.shreyaspatil.ai.client.generativeai.GenerativeModel
import dev.shreyaspatil.ai.client.generativeai.type.FunctionType
import dev.shreyaspatil.ai.client.generativeai.type.PlatformImage
import dev.shreyaspatil.ai.client.generativeai.type.Schema
import dev.shreyaspatil.ai.client.generativeai.type.content
import dev.shreyaspatil.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class GeminiAiModel(
  private val apiKey: String,
  private val modelName: String = "gemini-1.5-pro"
) : AiModel, AiComparisonResultFactory {
  override fun invoke(
    comparisonImageFilePath: String,
    aiCompareOptions: AiCompareOptions
  ): AiComparisonResult {
    val systemPrompt = aiCompareOptions.systemPrompt
    val generativeModel = GenerativeModel(
      modelName = modelName,
      apiKey = apiKey,
      systemInstruction = content {
        text(systemPrompt)
      },
      generationConfig = generationConfig {
        maxOutputTokens = 8192
        responseMimeType = "application/json"
        responseSchema = Schema(
          name = "content",
          description = "content",
          type = FunctionType.ARRAY,
          items = Schema(
            name = "assert_results",
            description = "An array of assertion results",
            type = FunctionType.OBJECT,
            properties = mapOf(
              "fulfillment_percent" to Schema.int(
                name = "fulfillment_percent",
                description = "A fulfillment percentage from 0 to 100",
              ),
              "explanation" to Schema(
                name = "explanation",
                description = "A brief explanation of how this percentage was determined. If fulfillment_percent is 100, this field should be empty.",
                type = FunctionType.STRING,
                nullable = true,
              )
            ),
            required = listOf("fulfillment_percent")
          ),
        )
      },
    )

    val template = aiCompareOptions.promptTemplate

    val inputPrompt = aiCompareOptions.inputPrompt(aiCompareOptions)
    val inputContent = content {
      image(readByteArrayFromFile(comparisonImageFilePath))
      val prompt = template.replace("INPUT_PROMPT", inputPrompt)
      text(prompt)

      debugLog {
        "RoborazziAi: prompt:$prompt"
      }
    }

    val response = runBlocking { generativeModel.generateContent(inputContent) }
    debugLog {
      "RoborazziAi: response: ${response.text}"
    }
    val geminiResult = CaptureResults.json.decodeFromString<Array<GeminiAiConditionResult>>(
      requireNotNull(
        response.text
      )
    )
    return AiComparisonResult(
      aiConditionResults = aiCompareOptions.aiConditions.mapIndexed { index, it ->
        val assertResult = geminiResult.getOrNull(index) ?: GeminiAiConditionResult(
          fulfillmentPercent = 0,
          explanation = "AI model did not return a result for this assertion"
        )
        AiConditionResult(
          assertPrompt = it.assertPrompt,
          requiredFulfillmentPercent = it.requiredFulfillmentPercent,
          fulfillmentPercent = assertResult.fulfillmentPercent,
          explanation = assertResult.explanation,
        )
      }
    )
  }
}


@Serializable
data class GeminiAiConditionResult(
  @SerialName("fulfillment_percent")
  val fulfillmentPercent: Int,
  val explanation: String?,
)

expect fun readByteArrayFromFile(filePath: String): PlatformImage
