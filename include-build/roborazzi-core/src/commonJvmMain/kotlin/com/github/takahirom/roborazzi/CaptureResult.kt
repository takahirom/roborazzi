package com.github.takahirom.roborazzi

import org.json.JSONObject
import java.io.File
import java.io.FileReader

sealed interface CaptureResult {
  fun toJson(): JSONObject
  val timestampNs: Long

  data class Recorded(
    val goldenFile: File,
    override val timestampNs: Long,
  ) : CaptureResult {
    override fun toJson(): JSONObject {
      val json = JSONObject()
      json.put("type", "recorded")
      json.put("golden_file_path", goldenFile.absolutePath)
      json.put("timestamp", timestampNs)
      return json
    }
  }

  data class Added(
    val compareFile: File?,
    val actualFile: File,
    val actualHashFile: File?,
    override val timestampNs: Long,
  ) : CaptureResult {
    override fun toJson(): JSONObject {
      val json = JSONObject()
      json.put("type", "added")
      compareFile?.let {
        json.put("compare_file_path", it.absolutePath)
      }
      actualHashFile?.let {
        json.put("actual_hash_file_path", it.absolutePath)
      }
      json.put("timestamp", timestampNs)
      return json
    }
  }

  sealed interface Changed : CaptureResult {
    data class FileChanged(
      val compareFile: File,
      val actualFile: File,
      val goldenFile: File,
      override val timestampNs: Long,
    ) : Changed {
      override fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("type", "changed")
        json.put("compare_file_path", compareFile.absolutePath)
        json.put("actual_file_path", actualFile.absolutePath)
        json.put("golden_file_path", goldenFile.absolutePath)
        json.put("timestamp", timestampNs)
        return json
      }
    }

    data class HashChanged(
      val goldenHashFile: File,
      val actualHashFile: File,
      val actualFile: File,
      override val timestampNs: Long,
    ) : Changed {
      override fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("type", "hash_changed")
        json.put("actual_file_path", actualFile.absolutePath)
        json.put("golden_hash_file_path", goldenHashFile.absolutePath)
        json.put("actual_hash_file_path", actualHashFile.absolutePath)
        json.put("timestamp", timestampNs)
        return json
      }
    }
  }

  data class Unchanged(
    val goldenFile: File,
    val goldenHashFile: File?,
    override val timestampNs: Long
  ) : CaptureResult {
    override fun toJson(): JSONObject {
      val json = JSONObject()
      json.put("type", "unchanged")
      json.put("golden_file_path", goldenFile.absolutePath)
      goldenHashFile?.let {
        json.put("golden_hash_file_path", it.absolutePath)
      }
      json.put("timestamp", timestampNs)
      return json
    }
  }

  companion object {
    fun fromJsonFile(inputPath: String): CaptureResult {
      val json = JSONObject(FileReader(inputPath).readText())
      return fromJson(json)
    }

    fun fromJson(json: JSONObject): CaptureResult {
      val type = json.getString("type")
      val compareFile = json.optString("compare_file_path")?.let { File(it) }
      val goldenFile = json.optString("golden_file_path")?.let { File(it) }
      val goldenHashFile = json.optString("golden_hash_file_path")?.let { File(it) }
      val actualFile = json.optString("actual_file_path")?.let { File(it) }
      val actualHashFile = json.optString("actual_hash_file_path")?.let { File(it) }
      val timestampNs = json.getLong("timestamp")

      return when (type) {
        "recorded" -> Recorded(
          goldenFile = goldenFile!!,
          timestampNs = timestampNs
        )

        "changed" -> Changed.FileChanged(
          compareFile = compareFile!!,
          goldenFile = goldenFile!!,
          actualFile = actualFile!!,
          timestampNs = timestampNs
        )

        "hash_changed" -> Changed.HashChanged(
          goldenHashFile = goldenHashFile!!,
          actualHashFile = actualHashFile!!,
          actualFile = actualFile!!,
          timestampNs = timestampNs
        )

        "unchanged" -> Unchanged(
          goldenFile = goldenFile!!,
          goldenHashFile = goldenHashFile,
          timestampNs = timestampNs
        )

        "added" -> Added(
          compareFile = compareFile,
          actualFile = actualFile!!,
          actualHashFile = actualHashFile,
          timestampNs = timestampNs,
        )

        else -> throw IllegalArgumentException("Unknown type $type")
      }
    }
  }
}
