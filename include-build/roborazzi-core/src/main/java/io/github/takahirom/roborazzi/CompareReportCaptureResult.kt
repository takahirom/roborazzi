package io.github.takahirom.roborazzi

import java.io.File
import java.io.FileReader
import org.json.JSONObject

sealed interface CompareReportCaptureResult {
  fun toJson(): JSONObject
  val timestampNs: Long
  val compareFile: File?
  val actualFile: File?
  val goldenFile: File?

  data class Added(
    override val compareFile: File,
    override val actualFile: File,
    override val goldenFile: File,
    override val timestampNs: Long,
  ) : CompareReportCaptureResult {
    override fun toJson(): JSONObject {
      val json = JSONObject()
      json.put("type", "added")
      json.put("compare_file_path", compareFile.absolutePath)
      json.put("actual_file_path", actualFile.absolutePath)
      json.put("golden_file_path", goldenFile.absolutePath)
      json.put("timestamp", timestampNs)
      return json
    }
  }

  data class Changed(
    override val compareFile: File,
    override val goldenFile: File,
    override val actualFile: File,
    override val timestampNs: Long
  ) : CompareReportCaptureResult {
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

  data class Unchanged(
    override val goldenFile: File,
    override val timestampNs: Long
  ) : CompareReportCaptureResult {
    override val actualFile: File?
      get() = null
    override val compareFile: File?
      get() = null

    override fun toJson(): JSONObject {
      val json = JSONObject()
      json.put("type", "unchanged")
      json.put("golden_file_path", goldenFile.absolutePath)
      json.put("timestamp", timestampNs)
      return json
    }
  }

  companion object {
    fun fromJsonFile(inputPath: String): CompareReportCaptureResult {
      val json = JSONObject(FileReader(inputPath).readText())
      return fromJson(json)
    }

    fun fromJson(json: JSONObject): CompareReportCaptureResult {
      val type = json.getString("type")
      val compareFile = json.optString("compare_file_path")?.let { File(it) }
      val goldenFile = json.optString("golden_file_path")?.let { File(it) }
      val actualFile = json.optString("actual_file_path")?.let { File(it) }
      val timestampNs = json.getLong("timestamp")

      return when (type) {
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
