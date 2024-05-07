package com.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.CaptureResults.Companion.json
import kotlinx.serialization.Contextual
import kotlinx.serialization.InternalSerializationApi
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
import kotlinx.serialization.serializer
import java.io.File
import java.io.FileReader
import java.lang.IllegalArgumentException

@Serializable(with = CaptureResult.CaptureResultSerializer::class)
sealed interface CaptureResult {
  val type: String
  val timestampNs: Long
  val compareFile: File?
  val actualFile: File?
  val goldenFile: File?
  val contextData: Map<String,@Contextual Any>

  val reportFile: File
    get() = when (val result = this) {
      is Added -> result.actualFile
      is Changed -> result.compareFile
      is Recorded -> result.goldenFile
      is Unchanged -> result.goldenFile
    }

  @Serializable
  data class Recorded(
    @SerialName("golden_file_path")
    override val goldenFile:@Contextual File,
    @SerialName("timestamp")
    override val timestampNs: Long,
    @SerialName("context_data")
    override val contextData: Map<String,@Contextual Any>
  ) : CaptureResult {
    override val type = "recorded"
    override val actualFile: File?
      get() = null
    override val compareFile: File?
      get() = null
  }

  @Serializable
  data class Added(
    @SerialName("compare_file_path")
    override val compareFile:@Contextual File,
    @SerialName("actual_file_path")
    override val actualFile:@Contextual File,
    @SerialName("golden_file_path")
    override val goldenFile:@Contextual File,
    @SerialName("timestamp")
    override val timestampNs: Long,
    @SerialName("context_data")
    override val contextData: Map<String,@Contextual Any>
  ) : CaptureResult {
    override val type = "added"
  }

  @Serializable
  data class Changed(
    @SerialName("compare_file_path")
    override val compareFile:@Contextual File,
    @SerialName("golden_file_path")
    override val goldenFile:@Contextual File,
    @SerialName("actual_file_path")
    override val actualFile:@Contextual File,
    @SerialName("timestamp")
    override val timestampNs: Long,
    @SerialName("context_data")
    override val contextData: Map<String,@Contextual Any>
  ) : CaptureResult {
    override val type = "changed"
  }

  @Serializable
  data class Unchanged(
    @SerialName("golden_file_path")
    override val goldenFile:@Contextual File,
    @SerialName("timestamp")
    override val timestampNs: Long,
    @SerialName("context_data")
    override val contextData: Map<String,@Contextual Any>
  ) : CaptureResult {
    override val type = "unchanged"
    override val actualFile: File?
      get() = null
    override val compareFile: File?
      get() = null
  }

  companion object {
    fun fromJsonFile(filePath: String): CaptureResult {
      val jsonElement = json.parseToJsonElement(FileReader(filePath).readText())
      return json.decodeFromJsonElement<CaptureResult>(jsonElement)
    }
  }

  object CaptureResultSerializer : KSerializer<CaptureResult> {
    override val descriptor: SerialDescriptor
      get() = PrimitiveSerialDescriptor("CaptureResult", PrimitiveKind.STRING)

    @OptIn(InternalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: CaptureResult) =
      when (value) {
        is Recorded -> encoder.encodeSerializableValue(Recorded::class.serializer(), value)
        is Changed -> encoder.encodeSerializableValue(Changed::class.serializer(), value)
        is Unchanged -> encoder.encodeSerializableValue(Unchanged::class.serializer(), value)
        is Added -> encoder.encodeSerializableValue(Added::class.serializer(), value)
      }

    @OptIn(InternalSerializationApi::class)
    override fun deserialize(decoder: Decoder): CaptureResult {
      require(decoder is JsonDecoder)
      val type = decoder.decodeJsonElement().jsonObject["type"]!!.jsonPrimitive.content
      return when (type) {
      "recorded" -> decoder.decodeSerializableValue(Recorded::class.serializer())
      "changed" -> decoder.decodeSerializableValue(Changed::class.serializer())
      "unchanged" -> decoder.decodeSerializableValue(Unchanged::class.serializer())
      "added" -> decoder.decodeSerializableValue(Added::class.serializer())
      else -> throw IllegalArgumentException("Unknown type $type")
      }
    }
  }
}
