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

  fun toHtml(reportDirectoryPath: String): String {
    fun File.pathFrom(reportDirectoryPath: String): String {
      val reportDirectory = File(reportDirectoryPath)
      val relativePath = relativeTo(reportDirectory)
      return relativePath.path
    }

    val recordedImages = captureResults.filterIsInstance<CaptureResult.Recorded>()
    val addedImages = captureResults.filterIsInstance<CaptureResult.Added>()
    val changedImages = captureResults.filterIsInstance<CaptureResult.Changed>()
    val unchangedImages = captureResults.filterIsInstance<CaptureResult.Unchanged>()
    fun buildTable(title: String, anchor: String, images: List<CaptureResult>): String {
      if (images.isEmpty()) return ""
      return buildString {
        append("<h3>$title (${images.size})</h3>")
        val fileNameClass = "flow-text col s3"
        val fileNameStyle = "word-wrap: break-word; word-break: break-all;"
        val imgClass = "col s7"
        val imgAttributes = "style=\"width: 100%; height: 100%; object-fit: cover;\""
        append("<table class=\"highlight\" id=\"$anchor\">")
        append("<thead>")
        append("<tr class=\"row\">")
        append("<th class=\"$fileNameClass\" style=\"$fileNameStyle\">File Name</th>")
        append("<th class=\"$imgClass flow-text \">Image</th>")
        append("</tr>")
        append("</thead>")
        append("<tbody>")
        images.forEach { image ->
          append("<tr class=\"row\">")

          when (image) {
            is CaptureResult.Added -> {
              append("<td class=\"$fileNameClass\" style=\"$fileNameStyle\">${image.actualFile.name}</td>")
              append("<td class=\"$imgClass\"><img $imgAttributes src=\"${image.actualFile.pathFrom(reportDirectoryPath)}\"/></td>")
            }

            is CaptureResult.Changed -> {
              append("<td class=\"$fileNameClass\" style=\"$fileNameStyle\">${image.compareFile.name}</td>")
              append("<td class=\"$imgClass\"><img $imgAttributes src=\"${image.compareFile.pathFrom(reportDirectoryPath)}\"/></td>")
            }

            is CaptureResult.Recorded -> {
              append("<td class=\"$fileNameClass\" style=\"$fileNameStyle\">${image.goldenFile.name}</td>")
              append("<td class=\"$imgClass\"><img $imgAttributes src=\"${image.goldenFile.pathFrom(reportDirectoryPath)}\"/></td>")
            }

            is CaptureResult.Unchanged -> {
              append("<td class=\"$fileNameClass\" style=\"$fileNameStyle\">${image.goldenFile.name}</td>")
              append("<td class=\"$imgClass\"><img $imgAttributes src=\"${image.goldenFile.pathFrom(reportDirectoryPath)}\"/></td>")
            }
          }
          append("</tr>")
        }
        append("</tbody>")
        append("</table>")

      }
    }
    return buildString {
      append(summary.toHtml())
      append(buildTable("Recorded images", "recorded", recordedImages))
      append(buildTable("Added images", "added", addedImages))
      append(buildTable("Changed images", "changed", changedImages))
      append(buildTable("Unchanged images", "unchanged", unchangedImages))
    }
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

    fun from(results: List<CaptureResult>): CaptureResults {
      return CaptureResults(
        summary = ResultSummary(
          total = results.size,
          recorded = results.count { it is CaptureResult.Recorded },
          added = results.count { it is CaptureResult.Added },
          changed = results.count { it is CaptureResult.Changed },
          unchanged = results.count { it is CaptureResult.Unchanged }
        ),
        captureResults = results
      )
    }
  }
}
