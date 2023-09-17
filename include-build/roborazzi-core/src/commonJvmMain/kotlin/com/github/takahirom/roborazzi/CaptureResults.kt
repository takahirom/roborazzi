package com.github.takahirom.roborazzi

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CaptureResults(
  val summary: ResultSummary,
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
      val summary = ResultSummary.fromJson(jsonObject.getJSONObject("summary"))
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
