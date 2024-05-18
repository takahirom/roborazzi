package com.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.CaptureResults2.Companion.json
import kotlinx.io.files.Path
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

@Serializable(with = CaptureResult2.CaptureResultSerializer::class)
sealed interface CaptureResult2 {
  val type: String
  val timestampNs: Long
  val compareFile: Path?
  val actualFile: Path?
  val goldenFile: Path?
  val contextData: Map<String,@Contextual Any>

  val reportFile: Path
    get() = when (val result = this) {
      is Added -> result.actualFile
      is Changed -> result.compareFile
      is Recorded -> result.goldenFile
      is Unchanged -> result.goldenFile
    }

  @Serializable
  data class Recorded(
    @SerialName("golden_file_path")
    override val goldenFile:@Contextual Path,
    @SerialName("timestamp")
    override val timestampNs: Long,
    @SerialName("context_data")
    override val contextData: Map<String,@Contextual Any>
  ) : CaptureResult2 {
    override val type = "recorded"
    override val actualFile: Path?
      get() = null
    override val compareFile: Path?
      get() = null
  }

  @Serializable
  data class Added(
    @SerialName("compare_file_path")
    override val compareFile:@Contextual Path,
    @SerialName("actual_file_path")
    override val actualFile:@Contextual Path,
    @SerialName("golden_file_path")
    override val goldenFile:@Contextual Path,
    @SerialName("timestamp")
    override val timestampNs: Long,
    @SerialName("context_data")
    override val contextData: Map<String,@Contextual Any>
  ) : CaptureResult2 {
    override val type = "added"
  }

  @Serializable
  data class Changed(
    @SerialName("compare_file_path")
    override val compareFile:@Contextual Path,
    @SerialName("golden_file_path")
    override val goldenFile:@Contextual Path,
    @SerialName("actual_file_path")
    override val actualFile:@Contextual Path,
    @SerialName("timestamp")
    override val timestampNs: Long,
    @SerialName("context_data")
    override val contextData: Map<String,@Contextual Any>
  ) : CaptureResult2 {
    override val type = "changed"
  }

  @Serializable
  data class Unchanged(
    @SerialName("golden_file_path")
    override val goldenFile:@Contextual Path,
    @SerialName("timestamp")
    override val timestampNs: Long,
    @SerialName("context_data")
    override val contextData: Map<String,@Contextual Any>
  ) : CaptureResult2 {
    override val type = "unchanged"
    override val actualFile: Path?
      get() = null
    override val compareFile: Path?
      get() = null
  }

  companion object {
    fun fromJsonFile(filePath: String): CaptureResult2 {
      val string = KotlinIo.readText(Path(filePath))
      val jsonElement = json.parseToJsonElement(string)
      return json.decodeFromJsonElement<CaptureResult2>(jsonElement)
    }
  }

  object CaptureResultSerializer : KSerializer<CaptureResult2> {
    override val descriptor: SerialDescriptor
      get() = PrimitiveSerialDescriptor("CaptureResult", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CaptureResult2) =
      when (value) {
        is Recorded -> encoder.encodeSerializableValue(Recorded.serializer(), value)
        is Changed -> encoder.encodeSerializableValue(Changed.serializer(), value)
        is Unchanged -> encoder.encodeSerializableValue(Unchanged.serializer(), value)
        is Added -> encoder.encodeSerializableValue(Added.serializer(), value)
      }

    override fun deserialize(decoder: Decoder): CaptureResult2 {
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
