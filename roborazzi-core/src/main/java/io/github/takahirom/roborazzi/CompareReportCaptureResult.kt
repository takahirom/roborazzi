package io.github.takahirom.roborazzi

import android.util.JsonReader
import android.util.JsonWriter
import java.io.File
import java.io.FileReader

sealed class CompareReportCaptureResult {
  abstract fun writeJson(writer: JsonWriter)

  abstract val timestampNs: Long

  data class Added(
    val compareFile: File,
    override val timestampNs: Long
  ) : CompareReportCaptureResult() {
    override fun writeJson(writer: JsonWriter) {
      writer.beginObject()
      writer.name("type").value("added")
      writer.name("compare_file_path").value(compareFile.absolutePath)
      writer.name("timestamp").value(timestampNs)
      writer.endObject()
    }
  }

  data class Changed(
    val compareFile: File,
    val goldenFile: File,
    override val timestampNs: Long
  ) : CompareReportCaptureResult() {
    override fun writeJson(writer: JsonWriter) {
      writer.beginObject()
      writer.name("type").value("changed")
      writer.name("compare_file_path").value(compareFile.absolutePath)
      writer.name("golden_file_path").value(goldenFile.absolutePath)
      writer.name("timestamp").value(timestampNs)
      writer.endObject()
    }
  }

  data class Unchanged(
    val goldenFile: File,
    override val timestampNs: Long
  ) : CompareReportCaptureResult() {
    override fun writeJson(writer: JsonWriter) {
      writer.beginObject()
      writer.name("type").value("unchanged")
      writer.name("golden_file_path").value(goldenFile.absolutePath)
      writer.name("timestamp").value(timestampNs)
      writer.endObject()
    }
  }

  companion object {
    fun fromJsonFile(inputPath: String): CompareReportCaptureResult {
      FileReader(inputPath).use { fileReader ->
        return JsonReader(fileReader).use { fromJsonReader(it) }
      }
    }

    fun fromJsonReader(jsonReader: JsonReader): CompareReportCaptureResult {
      var type: String? = null
      var compareFile: String? = null
      var goldenFile: String? = null
      var timestampNs: Long? = null

      jsonReader.beginObject()
      while (jsonReader.hasNext()) {
        when (jsonReader.nextName()) {
          "type" -> type = jsonReader.nextString()
          "compare_file_path" -> compareFile = jsonReader.nextString()
          "golden_file_path" -> goldenFile = jsonReader.nextString()
          "timestamp" -> timestampNs = jsonReader.nextLong()
          else -> jsonReader.skipValue()
        }
      }
      jsonReader.endObject()

      val captureResult = when (type) {
        "changed" -> Changed(
          compareFile = File(compareFile),
          goldenFile = File(goldenFile),
          timestampNs = timestampNs!!
        )

        "unchanged" -> Unchanged(
          goldenFile = File(goldenFile),
          timestampNs = timestampNs!!
        )

        "added" -> Added(
          compareFile = File(compareFile),
          timestampNs = timestampNs!!
        )

        else -> throw IllegalArgumentException("Unknown type $type")
      }
      return captureResult
    }
  }
}