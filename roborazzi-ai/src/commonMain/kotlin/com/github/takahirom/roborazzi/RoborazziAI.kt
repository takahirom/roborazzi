package com.github.takahirom.roborazzi

import dev.shreyaspatil.ai.client.generativeai.GenerativeModel
import dev.shreyaspatil.ai.client.generativeai.type.FunctionType
import dev.shreyaspatil.ai.client.generativeai.type.PlatformImage
import dev.shreyaspatil.ai.client.generativeai.type.Schema
import dev.shreyaspatil.ai.client.generativeai.type.content
import dev.shreyaspatil.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@InternalRoborazziApi
val loaded = run {
  aiCompareResultFactory = AiCompareResultFactory { comparisonImageFilePath, aiOptions ->
    createAiResult(aiOptions, comparisonImageFilePath)
  }
}

fun loadRoboAi() = loaded

@Serializable
class GeminiResult(
  @SerialName("fulfillment_percent")
  val fulfillmentPercent: Int,
  val explanation: String?,
)

expect fun readByteArrayFromFile(filePath: String): PlatformImage

@InternalRoborazziApi
fun createAiResult(
  aiOptions: AiOptions,
  comparisonImageFilePath: String,
): AiResult {
  when (val aiModel = aiOptions.aiModel) {
      is AiOptions.AiModel.Gemini -> {
        val generativeModel = GenerativeModel(
          modelName = aiModel.modelName,
          apiKey = aiModel.apiKey,
          generationConfig = generationConfig {
            maxOutputTokens = 8192
            responseMimeType = "application/json"
            responseSchema = Schema.obj(
              name = "content",
              description = "content",
              Schema.int("fulfillment_percent", "A fulfillment percentage from 0 to 100"),
              Schema(
                name = "explanation",
                description = "A brief explanation of how this percentage was determined. If fulfillment_percent is 100, this field should be empty.",
                type = FunctionType.STRING,
                nullable = true,
              )
            )
          },
        )

        val prompt = aiOptions.prompt
        val template = aiOptions.template

        val inputContent = content {
          image(readByteArrayFromFile(comparisonImageFilePath))
          text(template.replace("PROMPT", prompt))
        }

        val response = runBlocking { generativeModel.generateContent(inputContent) }
        val geminiResult = CaptureResults.json.decodeFromString<GeminiResult>(
          requireNotNull(
            response.text
          )
        )
        return AiResult(
          prompt = prompt,
          fulfillment = geminiResult.fulfillmentPercent,
          requiredFulfillmentPercent = aiOptions.requiredFulfillmentPercent,
          explanation = geminiResult.explanation,
        )
      }

    is AiOptions.AiModel.Manual -> {
      return aiModel(comparisonImageFilePath, aiOptions)
    }

    else -> {
      throw NotImplementedError("aiCompareCanvasFactory for $aiModel is not implemented in this version")
    }
  }
}