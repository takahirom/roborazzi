package com.github.takahirom.roborazzi

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class CompareSummary(
  val total: Int,
  val added: Int,
  val changed: Int,
  val unchanged: Int
) {
  fun toJson(): JSONObject {
    val json = JSONObject()
    json.put("total", total)
    json.put("added", added)
    json.put("changed", changed)
    json.put("unchanged", unchanged)
    return json
  }

  companion object {
    fun fromJson(jsonObject: JSONObject): CompareSummary {
      val total = jsonObject.getInt("total")
      val added = jsonObject.getInt("added")
      val changed = jsonObject.getInt("changed")
      val unchanged = jsonObject.getInt("unchanged")
      return CompareSummary(total, added, changed, unchanged)
    }
  }
}

data class CaptureResults(
  val summary: CompareSummary,
  val captureResults: List<CaptureResult>
) {
  fun toJson(): JSONObject {
    val json = JSONObject()
    json.put("summary", summary.toJson())
    val resultsArray = JSONArray()
    captureResults.forEach { result ->
      resultsArray.put(result.toJson())
    }
    json.put("results", resultsArray)
    return json
  }

  companion object {
    fun fromJsonFile(inputPath: String): CaptureResults {
      val fileContents = File(inputPath).readText()
      val jsonObject = JSONObject(fileContents)
      return fromJson(jsonObject)
    }

    fun fromJson(jsonObject: JSONObject): CaptureResults {
      val summary = CompareSummary.fromJson(jsonObject.getJSONObject("summary"))
      val resultsArray = jsonObject.getJSONArray("results")
      val captureResults = mutableListOf<CaptureResult>()
      for (i in 0 until resultsArray.length()) {
        val resultJson = resultsArray.getJSONObject(i)
        captureResults.add(CaptureResult.fromJson(resultJson))
      }
      return CaptureResults(summary, captureResults)
    }
  }
}
