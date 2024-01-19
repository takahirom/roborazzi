package com.github.takahirom.roborazzi

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.io.FileReader

sealed interface CaptureResult {
  fun toJson(): JsonObject
  val timestampNs: Long
  val compareFile: File?
  val actualFile: File?
  val goldenFile: File?

  data class Recorded(
    override val goldenFile: File,
    override val timestampNs: Long,
  ) : CaptureResult {
    override val actualFile: File?
      get() = null
    override val compareFile: File?
      get() = null

    override fun toJson(): JsonObject {
      val json = JsonObject()
      json.addProperty("type", "recorded")
      json.addProperty("golden_file_path", goldenFile.absolutePath)
      json.addProperty("timestamp", timestampNs)
      return json
    }
  }

  data class Added(
    override val compareFile: File,
    override val actualFile: File,
    override val goldenFile: File,
    override val timestampNs: Long,
  ) : CaptureResult {
    override fun toJson(): JsonObject {
      val json = JsonObject()
      json.addProperty("type", "added")
      json.addProperty("compare_file_path", compareFile.absolutePath)
      json.addProperty("actual_file_path", actualFile.absolutePath)
      json.addProperty("golden_file_path", goldenFile.absolutePath)
      json.addProperty("timestamp", timestampNs)
      return json
    }
  }

  data class Changed(
    override val compareFile: File,
    override val goldenFile: File,
    override val actualFile: File,
    override val timestampNs: Long
  ) : CaptureResult {
    override fun toJson(): JsonObject {
      val json = JsonObject()
      json.addProperty("type", "changed")
      json.addProperty("compare_file_path", compareFile.absolutePath)
      json.addProperty("actual_file_path", actualFile.absolutePath)
      json.addProperty("golden_file_path", goldenFile.absolutePath)
      json.addProperty("timestamp", timestampNs)
      return json
    }
  }

  data class Unchanged(
    override val goldenFile: File,
    override val timestampNs: Long
  ) : CaptureResult {
    override val actualFile: File?
      get() = null
    override val compareFile: File?
      get() = null

    override fun toJson(): JsonObject {
      val json = JsonObject()
      json.addProperty("type", "unchanged")
      json.addProperty("golden_file_path", goldenFile.absolutePath)
      json.addProperty("timestamp", timestampNs)
      return json
    }
  }

  companion object {
    fun fromJsonFile(inputPath: String): CaptureResult {
      return Gson().fromJson(FileReader(inputPath).readText(), CaptureResult::class.java)
    }

    fun fromJson(json: JsonObject): CaptureResult {
      val type = json.get("type").asString
      val compareFile = json.get("compare_file_path")?.asString?.let{ File(it) }
      val goldenFile = json.get("golden_file_path")?.asString?.let{ File(it) }
      val actualFile = json.get("actual_file_path")?.asString?.let{ File(it) }
      val timestampNs = json.get("timestamp").asLong

      return when (type) {
        "recorded" -> Recorded(
          goldenFile = goldenFile!!,
          timestampNs = timestampNs
        )

        "changed" -> Changed(
          compareFile = compareFile!!,
          goldenFile = goldenFile!!,
          actualFile = actualFile!!,
          timestampNs = timestampNs
        )

        "unchanged" -> Unchanged(
          goldenFile = goldenFile!!,
          timestampNs = timestampNs
        )

        "added" -> Added(
          compareFile = compareFile!!,
          actualFile = actualFile!!,
          timestampNs = timestampNs,
          goldenFile = goldenFile!!,
        )

        else -> throw IllegalArgumentException("Unknown type $type")
      }
    }
  }
}
