package com.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.AiAssertionOptions.AiAssertionModel.Companion.DefaultMaxOutputTokens
import com.github.takahirom.roborazzi.AiAssertionOptions.AiAssertionModel.Companion.DefaultTemperature
import com.github.takahirom.roborazzi.CaptureResults.Companion.json
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@ExperimentalRoborazziApi
class OpenAiAiAssertionModel(
  private val apiKey: String,
  private val modelName: String = "gpt-4o",
  private val baseUrl: String = "https://api.openai.com/v1/",
  private val loggingEnabled: Boolean = false,
  private val temperature: Float = DefaultTemperature,
  private val maxTokens: Int = DefaultMaxOutputTokens,
  private val seed: Int = 1566,
  private val requestBuilderModifier: (HttpRequestBuilder.() -> Unit) = {
    header("Authorization", "Bearer $apiKey")
  }
) : AiAssertionOptions.AiAssertionModel {
  private val httpClient: HttpClient = HttpClient {
    install(ContentNegotiation) {
      json(
        json = json
      )
    }
    // log
    if (loggingEnabled) {
      install(Logging) {
        logger = Logger.SIMPLE
        level = LogLevel.ALL
      }
    }
  }

  override fun assert(
    referenceImageFilePath: String,
    comparisonImageFilePath: String,
    actualImageFilePath: String,
    aiAssertionOptions: AiAssertionOptions
  ): AiAssertionResults {
    val systemPrompt = aiAssertionOptions.systemPrompt
    val template = aiAssertionOptions.promptTemplate
    val inputPrompt = aiAssertionOptions.inputPrompt(aiAssertionOptions)
    val imageBytes = readByteArrayFromFile(comparisonImageFilePath)
    val imageBase64 = imageBytes.encodeBase64()
    val messages = listOf(
      Message(
        role = "system",
        content = listOf(
          Content(
            type = "text",
            text = systemPrompt
          )
        ),
      ),
      Message(
        role = "user",
        content = listOf(
          Content(
            type = "text",
            text = template.replace("INPUT_PROMPT", inputPrompt)
          ),
          Content(
            type = "image_url",
            imageUrl = ImageUrl(
              url = "data:image/png;base64,$imageBase64"
            )
          )
        )
      )
    )
    val responseText = runBlocking {
      chatCompletion(
        messages = messages,
        model = modelName
      )
    }
    debugLog {
      "OpenAiAiModel: response: $responseText"
    }
    val aiConditionResults = parseOpenAiResponse(responseText, aiAssertionOptions)
    return AiAssertionResults(
      aiAssertionResults = aiConditionResults
    )
  }

  private suspend fun chatCompletion(
    messages: List<Message>,
    model: String
  ): String {
    val requestBody = ChatCompletionRequest(
      model = model,
      messages = messages,
      temperature = temperature,
      maxTokens = maxTokens,
      responseFormat = ResponseFormat(
        type = "json_schema",
        jsonSchema = buildJsonSchema(),
      ),
      seed = seed
    )
    val response: HttpResponse = httpClient.post(baseUrl + "chat/completions") {
      requestBuilderModifier()
      contentType(ContentType.Application.Json)
      setBody(requestBody)
    }
    val bodyText = response.bodyAsText()
    debugLog { "OpenAiAiModel: response: $bodyText" }
    val responseBody: ChatCompletionResponse = json.decodeFromString(bodyText)
    return responseBody.choices.firstOrNull()?.message?.content ?: ""
  }

  private fun buildJsonSchema(): JsonObject {
    val schemaJson = """
    {
  "name": "OpenAiResponse1",
  "description": "Verify image",
  "strict": true,
  "schema": {
    "type": "object",
    "required": ["results"],
    "additionalProperties": false,
    "properties": {
      "results": {
        "type": "array",
        "items": {
          "type": "object",
          "required": ["fulfillment_percent", "explanation"],
          "additionalProperties": false,
          "properties": {
            "fulfillment_percent": {
              "type": "integer"
            },
            "explanation": {
              "type": ["string", "null"]
            }
          }
        }
      }
    }
  }
}
    """.trimIndent()
    val json = Json { ignoreUnknownKeys = true }
    return json.parseToJsonElement(schemaJson).jsonObject
  }
}


private fun parseOpenAiResponse(
  responseText: String,
  aiAssertionOptions: AiAssertionOptions
): List<AiAssertionResult> {
  val openAiResult = try {
    val element = json.parseToJsonElement(responseText)
    val resultsElement = element.jsonObject["results"]
    val results = if (resultsElement!=null) {
      json.decodeFromJsonElement<List<OpenAiConditionResult>>(resultsElement)
    } else {
      emptyList()
    }
    OpenAiResponse(results = results)
  } catch (e: Exception) {
    debugLog { "Failed to parse OpenAI response: ${e.message}" }
    OpenAiResponse(results = emptyList())
  }
  return aiAssertionOptions.aiAssertions.mapIndexed { index, condition ->
    val result = openAiResult.results.getOrNull(index)
    val fulfillmentPercent = result?.fulfillmentPercent ?: 0
    val explanation = result?.explanation ?: "AI model did not return a result for this assertion"
    AiAssertionResult(
      assertPrompt = condition.assertPrompt,
      requiredFulfillmentPercent = condition.requiredFulfillmentPercent,
      failIfNotFulfilled = condition.failIfNotFulfilled,
      fulfillmentPercent = fulfillmentPercent,
      explanation = explanation,
    )
  }
}

private fun readByteArrayFromFile(filePath: String): ByteArray {
  return SystemFileSystem.source(path = Path(filePath)).buffered().readByteArray()
}

@OptIn(ExperimentalEncodingApi::class)
private fun ByteArray.encodeBase64(): String {
  return Base64.encode(this)
}

// Request

@Serializable
private data class ChatCompletionRequest(
  val model: String,
  val messages: List<Message>,
  val temperature: Float,
  @SerialName("max_completion_tokens") val maxTokens: Int,
  @SerialName("response_format") val responseFormat: ResponseFormat?,
  val seed: Int,
)

@Serializable
private data class ResponseFormat(
  val type: String,
  @SerialName("json_schema") val jsonSchema: JsonObject
)

@Serializable
private data class Message(
  val role: String,
  val content: List<Content>
)

@Serializable
private data class Content(
  val type: String,
  val text: String? = null,
  @SerialName("image_url") val imageUrl: ImageUrl? = null
)

@Serializable
private data class ImageUrl(
  val url: String
)

@Serializable
private data class ChatCompletionResponse(
  val id: String,
  val `object`: String,
  val created: Long,
  val model: String,
  val choices: List<Choice>,
  val usage: Usage? = null
)

@Serializable
private data class Choice(
  val index: Int,
  val message: ChoiceMessage,
  @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
private data class ChoiceMessage(
  val role: String,
  val content: String
)

@Serializable
private data class Usage(
  @SerialName("prompt_tokens") val promptTokens: Int,
  @SerialName("completion_tokens") val completionTokens: Int? = null,
  @SerialName("total_tokens") val totalTokens: Int,
)


// Response

@Serializable
private data class OpenAiResponse(
  val results: List<OpenAiConditionResult>
)

@Serializable
private data class OpenAiConditionResult(
  @SerialName("fulfillment_percent")
  val fulfillmentPercent: Int,
  val explanation: String?,
)