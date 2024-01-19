package com.github.takahirom.roborazzi

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.io.FileReader

data class CaptureResults(
  val summary: ResultSummary,
  val captureResults: List<CaptureResult>
) {
  fun toJson(): JsonObject {
    val json = JsonObject()
    json.add("summary", summary.toJson())
    val resultsArray = JsonArray()
    captureResults.forEach { result ->
      resultsArray.add(result.toJson())
    }
    json.add("results", resultsArray)
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
        val imgAttributes = "style=\"max-width: 100%; height: 100%; object-fit: cover;\" class=\"modal-trigger\""
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
              append("<td class=\"$imgClass\"><img $imgAttributes src=\"${image.actualFile.pathFrom(reportDirectoryPath)}\" data-alt=\"${image.goldenFile.name}\"/></td>")
            }

            is CaptureResult.Changed -> {
              append("<td class=\"$fileNameClass\" style=\"$fileNameStyle\">${image.compareFile.name}</td>")
              append("<td class=\"$imgClass\"><img $imgAttributes src=\"${image.compareFile.pathFrom(reportDirectoryPath)}\" data-alt=\"${image.goldenFile.name}\"/></td>")
            }

            is CaptureResult.Recorded -> {
              append("<td class=\"$fileNameClass\" style=\"$fileNameStyle\">${image.goldenFile.name}</td>")
              append("<td class=\"$imgClass\"><img $imgAttributes src=\"${image.goldenFile.pathFrom(reportDirectoryPath)}\" data-alt=\"${image.goldenFile.name}\"/></td>")
            }

            is CaptureResult.Unchanged -> {
              append("<td class=\"$fileNameClass\" style=\"$fileNameStyle\">${image.goldenFile.name}</td>")
              append("<td class=\"$imgClass\"><img $imgAttributes src=\"${image.goldenFile.pathFrom(reportDirectoryPath)}\" data-alt=\"${image.goldenFile.name}\" /></td>")
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
      return Gson().fromJson(FileReader(inputPath).readText(), CaptureResults::class.java)
    }

    fun fromJson(jsonObject: JsonObject): CaptureResults {
      val summary = ResultSummary.fromJson(jsonObject.get("summary").asJsonObject)
      val resultsArray = jsonObject.get("results").asJsonArray
      val captureResults = mutableListOf<CaptureResult>()
      for (i in 0 until resultsArray.size()) {
        val resultJson = resultsArray.get(i).asJsonObject
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
