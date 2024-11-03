package com.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.CaptureResults.Companion.json
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable(with = CaptureResult.CaptureResultSerializer::class)
sealed interface CaptureResult {
  val type: String
  val timestampNs: Long
  val compareFile: String?
  val actualFile: String?
  val goldenFile: String?
  val contextData: Map<String, @Contextual Any>

  val reportFile: String
    get() = when (val result = this) {
      is Added -> result.actualFile
      is Changed -> result.compareFile
      is Recorded -> result.goldenFile
      is Unchanged -> result.goldenFile
    }

  @Serializable
  data class Recorded(
    @SerialName("golden_file_path")
    override val goldenFile: @Contextual String,
    @SerialName("timestamp")
    override val timestampNs: Long,
    @SerialName("context_data")
    override val contextData: Map<String, @Contextual Any>
  ) : CaptureResult {
    override val type = "recorded"
    override val actualFile: String?
      get() = null
    override val compareFile: String?
      get() = null
  }

  @Serializable
  data class Added(
    @SerialName("compare_file_path")
    override val compareFile: @Contextual String,
    @SerialName("actual_file_path")
    override val actualFile: @Contextual String,
    @SerialName("golden_file_path")
    override val goldenFile: @Contextual String,
    @SerialName("timestamp")
    override val timestampNs: Long,
    @SerialName("ai_assertion_results")
    val aiAssertionResults: AiAssertionResults?,
    @SerialName("context_data")
    override val contextData: Map<String, @Contextual Any>
  ) : CaptureResult {
    override val type = "added"
  }

  @Serializable
  data class Changed(
    @SerialName("compare_file_path")
    override val compareFile: @Contextual String,
    @SerialName("golden_file_path")
    override val goldenFile: @Contextual String,
    @SerialName("actual_file_path")
    override val actualFile: @Contextual String,
    @SerialName("timestamp")
    override val timestampNs: Long,
    @SerialName("diff_percentage")
    val diffPercentage: Float?,
    @SerialName("ai_assertion_results")
    val aiAssertionResults: AiAssertionResults?,
    @SerialName("context_data")
    override val contextData: Map<String, @Contextual Any>
  ) : CaptureResult {
    override val type = "changed"
  }

  @Serializable
  data class Unchanged(
    @SerialName("golden_file_path")
    override val goldenFile: @Contextual String,
    @SerialName("timestamp")
    override val timestampNs: Long,
    @SerialName("context_data")
    override val contextData: Map<String, @Contextual Any>
  ) : CaptureResult {
    override val type = "unchanged"
    override val actualFile: String?
      get() = null
    override val compareFile: String?
      get() = null
  }

  companion object {
    fun fromJsonFile(filePath: String): CaptureResult {
      val string = KotlinxIo.readText(filePath)
      val jsonElement = json.parseToJsonElement(string)
      return json.decodeFromJsonElement<CaptureResult>(jsonElement)
    }
  }

  object CaptureResultSerializer : KSerializer<CaptureResult> {
    override val descriptor: SerialDescriptor
      get() = PrimitiveSerialDescriptor("CaptureResult", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CaptureResult) =
      when (value) {
        is Recorded -> encoder.encodeSerializableValue(Recorded.serializer(), value)
        is Changed -> encoder.encodeSerializableValue(Changed.serializer(), value)
        is Unchanged -> encoder.encodeSerializableValue(Unchanged.serializer(), value)
        is Added -> encoder.encodeSerializableValue(Added.serializer(), value)
      }

    override fun deserialize(decoder: Decoder): CaptureResult {
      require(decoder is JsonDecoder)
      val type = decoder.decodeJsonElement().jsonObject["type"]!!.jsonPrimitive.content
      return when (type) {
        "recorded" -> decoder.decodeSerializableValue(Recorded.serializer())
        "changed" -> decoder.decodeSerializableValue(Changed.serializer())
        "unchanged" -> decoder.decodeSerializableValue(Unchanged.serializer())
        "added" -> decoder.decodeSerializableValue(Added.serializer())
        else -> throw IllegalArgumentException("Unknown type $type")
      }
    }
  }
}

@Serializable
data class AiAssertionResults(
  @SerialName("ai_assertion_results")
  val aiAssertionResults: List<AiAssertionResult> = emptyList()
)

@Serializable
data class AiAssertionResult(
  @SerialName("assert_prompt")
  val assertPrompt: String,
  @SerialName("required_fulfillment_percent")
  val requiredFulfillmentPercent: Int?,
  @SerialName("fail_if_not_fulfilled")
  val failIfNotFulfilled: Boolean,
  @SerialName("fulfillment_percent")
  val fulfillmentPercent: Int,
  @SerialName("explanation")
  val explanation: String?,
)