package com.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.CaptureResults.Companion.gson
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.io.File
import java.io.FileReader

@JsonAdapter(CaptureResult.JsonAdapter::class)
sealed interface CaptureResult {
  val type: String
  val timestampNs: Long
  val compareFile: File?
  val actualFile: File?
  val goldenFile: File?
  val contextData: Map<String, Any>

  val reportFile: File
    get() = when (val result = this) {
      is Added -> result.actualFile
      is Changed -> result.compareFile
      is Recorded -> result.goldenFile
      is Unchanged -> result.goldenFile
    }

  data class Recorded(
    @SerializedName("golden_file_path")
    override val goldenFile: File,
    @SerializedName("timestamp")
    override val timestampNs: Long,
    @SerializedName("context_data")
    override val contextData: Map<String, Any>
  ) : CaptureResult {

    override val type = "recorded"
    override val actualFile: File?
      get() = null
    override val compareFile: File?
      get() = null
  }

  data class Added(
    @SerializedName("compare_file_path")
    override val compareFile: File,
    @SerializedName("actual_file_path")
    override val actualFile: File,
    @SerializedName("golden_file_path")
    override val goldenFile: File,
    @SerializedName("timestamp")
    override val timestampNs: Long,
    @SerializedName("context_data")
    override val contextData: Map<String, Any>
  ) : CaptureResult {
    override val type = "added"
  }

  data class Changed(
    @SerializedName("compare_file_path")
    override val compareFile: File,
    @SerializedName("golden_file_path")
    override val goldenFile: File,
    @SerializedName("actual_file_path")
    override val actualFile: File,
    @SerializedName("timestamp")
    override val timestampNs: Long,
    @SerializedName("context_data")
    override val contextData: Map<String, Any>
  ) : CaptureResult {
    override val type = "changed"
  }

  data class Unchanged(
    @SerializedName("golden_file_path")
    override val goldenFile: File,
    @SerializedName("timestamp")
    override val timestampNs: Long,
    @SerializedName("context_data")
    override val contextData: Map<String, Any>
  ) : CaptureResult {
    override val type = "unchanged"
    override val actualFile: File?
      get() = null
    override val compareFile: File?
      get() = null
  }

  companion object {
    fun fromJsonFile(filePath: String): CaptureResult {
      return gson.fromJson(FileReader(filePath), CaptureResult::class.java)
    }
  }

  object JsonAdapter : com.google.gson.JsonSerializer<CaptureResult>,
    com.google.gson.JsonDeserializer<CaptureResult> {
    override fun serialize(
      src: CaptureResult,
      typeOfSrc: java.lang.reflect.Type,
      context: com.google.gson.JsonSerializationContext
    ): com.google.gson.JsonElement {
      val jsonElement = when (src) {
        is Recorded -> context.serialize(src, Recorded::class.java)
        is Changed -> context.serialize(src, Changed::class.java)
        is Unchanged -> context.serialize(src, Unchanged::class.java)
        is Added -> context.serialize(src, Added::class.java)
      }
      return jsonElement
    }

    override fun deserialize(
      json: com.google.gson.JsonElement,
      typeOfT: java.lang.reflect.Type,
      context: com.google.gson.JsonDeserializationContext
    ): CaptureResult? {
      val type = requireNotNull(json.asJsonObject.get("type")?.asString)
      return when (type) {
        "recorded" -> context.deserialize(json, Recorded::class.java)
        "changed" -> context.deserialize(json, Changed::class.java)
        "unchanged" -> context.deserialize(json, Unchanged::class.java)
        "added" -> context.deserialize(json, Added::class.java)
        else -> throw IllegalArgumentException("Unknown type $type")
      }
    }
  }
}
