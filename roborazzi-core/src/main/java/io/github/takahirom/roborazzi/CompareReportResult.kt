package io.github.takahirom.roborazzi

import android.util.JsonReader
import java.io.FileReader

data class Summary(
  val total: Int,
  val added: Int,
  val changed: Int,
  val unchanged: Int
) {
  companion object {
    fun fromJson(jsonReader: JsonReader): Summary {
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
      return Summary(
        total = total!!,
        added = added!!,
        changed = changed!!,
        unchanged = unchanged!!
      )
    }
  }
}

data class CompareReportResult(
  val summary: Summary,
  val compareReportCaptureResults: List<CompareReportCaptureResult>
) {

  companion object {
    fun fromJsonFile(inputPath: String): CompareReportResult {
      FileReader(inputPath).use { fileReader ->
        JsonReader(fileReader).use { jsonReader ->
          jsonReader.beginObject()
          var summary: Summary? = null
          var compareReportCaptureResults: List<CompareReportCaptureResult>? = null
          while (jsonReader.hasNext()) {
            when (jsonReader.nextName()) {
              "summary" -> summary = Summary.fromJson(jsonReader)
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