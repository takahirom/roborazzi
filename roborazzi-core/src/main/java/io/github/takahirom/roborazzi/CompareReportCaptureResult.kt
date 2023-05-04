package io.github.takahirom.roborazzi

import android.util.JsonReader
import android.util.JsonWriter
import java.io.FileReader

sealed class CompareReportCaptureResult {
  abstract fun writeJson(writer: JsonWriter)

  abstract val timestampNs: Long

  data class Added(
    val compareFilePath: String,
    override val timestampNs: Long
  ) : CompareReportCaptureResult() {
    override fun writeJson(writer: JsonWriter) {
      writer.beginObject()
      writer.name("type").value("added")
      writer.name("compare_file_path").value(compareFilePath)
      writer.name("timestamp").value(timestampNs)
      writer.endObject()
    }
  }

  data class Changed(
    val compareFilePath: String,
    val goldenFilePath: String,
    override val timestampNs: Long
  ) : CompareReportCaptureResult() {
    override fun writeJson(writer: JsonWriter) {
      writer.beginObject()
      writer.name("type").value("changed")
      writer.name("compare_file_path").value(compareFilePath)
      writer.name("golden_file_path").value(goldenFilePath)
      writer.name("timestamp").value(timestampNs)
      writer.endObject()
    }
  }

  data class Unchanged(
    val goldenFilePath: String,
    override val timestampNs: Long
  ) : CompareReportCaptureResult() {
    override fun writeJson(writer: JsonWriter) {
      writer.beginObject()
      writer.name("type").value("unchanged")
      writer.name("golden_file_path").value(goldenFilePath)
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
      var compareFilePath: String? = null
      var goldenFilePath: String? = null
      var timestampNs: Long? = null

      jsonReader.beginObject()
      while (jsonReader.hasNext()) {
        when (jsonReader.nextName()) {
          "type" -> type = jsonReader.nextString()
          "compare_file_path" -> compareFilePath = jsonReader.nextString()
          "golden_file_path" -> goldenFilePath = jsonReader.nextString()
          "timestamp" -> timestampNs = jsonReader.nextLong()
          else -> jsonReader.skipValue()
        }
      }
      jsonReader.endObject()

      val captureResult = when (type) {
        "changed" -> Changed(
          compareFilePath = checkNotNull(compareFilePath),
          goldenFilePath = checkNotNull(goldenFilePath),
          timestampNs = timestampNs!!
        )

        "unchanged" -> Unchanged(
          goldenFilePath = checkNotNull(goldenFilePath),
          timestampNs = timestampNs!!
        )

        "added" -> Added(
          compareFilePath = checkNotNull(compareFilePath),
          timestampNs = timestampNs!!
        )

        else -> throw IllegalArgumentException("Unknown type $type")
      }
      return captureResult
    }
  }
}