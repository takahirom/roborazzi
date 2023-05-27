package io.github.takahirom.roborazzi

import android.util.JsonReader
import android.util.JsonWriter
import java.io.FileReader

data class CompareSummary(
  val total: Int,
  val added: Int,
  val changed: Int,
  val unchanged: Int
) {
  fun writeJson(writer: JsonWriter) {
    writer.name("summary").beginObject()
    writer.name("total").value(total)
    writer.name("added").value(added)
    writer.name("changed").value(changed)
    writer.name("unchanged").value(unchanged)
    writer.endObject()
  }

  companion object {
    fun fromJson(jsonReader: JsonReader): CompareSummary {
      jsonReader.beginObject()
      var total: Int? = null
      var added: Int? = null
      var changed: Int? = null
      var unchanged: Int? = null
      while (jsonReader.hasNext()) {
        when (jsonReader.nextName()) {
          "total" -> total = jsonReader.nextInt()
          "added" -> added = jsonReader.nextInt()
          "changed" -> changed = jsonReader.nextInt()
          "unchanged" -> unchanged = jsonReader.nextInt()
          else -> jsonReader.skipValue()
        }
      }
      jsonReader.endObject()
      return CompareSummary(
        total = total!!,
        added = added!!,
        changed = changed!!,
        unchanged = unchanged!!
      )
    }
  }
}

data class CompareReportResult(
  val summary: CompareSummary,
  val compareReportCaptureResults: List<CompareReportCaptureResult>
) {
  fun writeJson(writer: JsonWriter) {
    writer.beginObject()
    summary.writeJson(writer)
    writer.name("results").beginArray()
    compareReportCaptureResults.forEach { result ->
      result.writeJson(writer)
    }
    writer.endArray()
    writer.endObject()
  }

  companion object {
    fun fromJsonFile(inputPath: String): CompareReportResult {
      FileReader(inputPath).use { fileReader ->
        JsonReader(fileReader).use { jsonReader ->
          jsonReader.beginObject()
          var summary: CompareSummary? = null
          var compareReportCaptureResults: List<CompareReportCaptureResult>? = null
          while (jsonReader.hasNext()) {
            when (jsonReader.nextName()) {
              "summary" -> summary = CompareSummary.fromJson(jsonReader)
              "results" -> {
                jsonReader.beginArray()
                val results = mutableListOf<CompareReportCaptureResult>()
                while (jsonReader.hasNext()) {
                  results.add(CompareReportCaptureResult.fromJsonReader(jsonReader))
                }
                jsonReader.endArray()
                compareReportCaptureResults = results
              }

              else -> jsonReader.skipValue()
            }
          }
          jsonReader.endObject()
          return CompareReportResult(
            summary = summary!!,
            compareReportCaptureResults = compareReportCaptureResults!!
          )
        }
      }
    }
  }
}