package com.github.takahirom.roborazzi

import android.graphics.BitmapFactory
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.FunctionType
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
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

@InternalRoborazziApi
fun createAiResult(
  aiOptions: RoborazziOptions.CompareOptions.AiOptions,
  comparisonImageFilePath: String,
): AiResult {
  val model = aiOptions.model
  if (model is RoborazziOptions.CompareOptions.AiOptions.Model.Gemini) {
    val generativeModel = GenerativeModel(
      modelName = model.modelName,
      apiKey = model.apiKey,
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
      image(BitmapFactory.decodeFile(comparisonImageFilePath))
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
  } else {
    throw NotImplementedError("aiCompareCanvasFactory for $model is not implemented in this version")
  }
}