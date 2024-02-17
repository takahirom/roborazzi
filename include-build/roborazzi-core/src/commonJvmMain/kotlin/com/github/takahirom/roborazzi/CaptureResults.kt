package com.github.takahirom.roborazzi

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.SerializedName
import java.io.File
import java.io.FileReader
import java.lang.reflect.Type

data class CaptureResults(
  @SerializedName("summary")
  val resultSummary: ResultSummary,
  @SerializedName("results")
  val captureResults: List<CaptureResult>
) {

  fun toJson(): String {
    return gson.toJson(this)
  }

  class Tab(
    val title: String,
    val id: String,
    val contents: String
  )

  fun toHtml(reportDirectoryPath: String): String {
    val resultTab = Tab(
      title = "Results",
      id = "results",
      contents = buildString {
        val recordedImages = captureResults.filterIsInstance<CaptureResult.Recorded>()
        val addedImages = captureResults.filterIsInstance<CaptureResult.Added>()
        val changedImages = captureResults.filterIsInstance<CaptureResult.Changed>()
        val unchangedImages = captureResults.filterIsInstance<CaptureResult.Unchanged>()
        append(resultSummary.toHtml())
        append(buildTable("Recorded images", "recorded", recordedImages, reportDirectoryPath))
        append(buildTable("Added images", "added", addedImages, reportDirectoryPath))
        append(buildTable("Changed images", "changed", changedImages, reportDirectoryPath))
        append(buildTable("Unchanged images", "unchanged", unchangedImages, reportDirectoryPath))
      }
    )
    val contextTabs = captureResults
      .flatMap { it.contextData.keys }
      .distinct()
      .map { key ->
        Tab(
          title = RoborazziReportConst.DefaultContextData.keyToTitle(key),
          id = key,
          contents = buildString {
            captureResults.groupBy { it.contextData[key]?.toString() ?: "undefined" }
              .toSortedMap(Comparator<String> { value1, value2 ->
                // show "undefined" and "null" at the end
                if (value1 == "undefined" || value2 == "null") {
                  1
                } else if (value1 == "null" || value2 == "undefined") {
                  -1
                } else {
                  value1.compareTo(value2)
                }
              })
              .forEach { (value, results) ->
                append(buildTable(value, value, results, reportDirectoryPath))
              }
          }
        )
      }
    val tabs = listOf(resultTab) + contextTabs
    return buildString {
      append(
        "<div class=\"row\">\n" +
          "<div class=\"col s12\">\n" +
          "<ul class=\"tabs\">"
      )
      tabs.forEach { tab ->
        // <li class="tab col s3"><a href="#test1">Test 1</a></li>
        append("""<li class="tab col s3"><a href="#${tab.id}">${tab.title}</a></li>""")
      }
      append("</ul>\n</div>")
      tabs.forEach { tab ->
        append("""<div id="${tab.id}" class="col s12" style="display: none;">${tab.contents}</div>""")
      }
      append("</div>")
    }
  }

  private fun buildTable(
    title: String,
    anchor: String,
    images: List<CaptureResult>,
    reportDirectoryPath: String
  ): String {
    fun File.pathFrom(reportDirectoryPath: String): String {
      val reportDirectory = File(reportDirectoryPath)
      val relativePath = relativeTo(reportDirectory)
      return relativePath.path
    }
    if (images.isEmpty()) return ""
    return buildString {
      append("<h3>$title (${images.size})</h3>")
      val fileNameClass = "flow-text col s3"
      val fileNameStyle = "word-wrap: break-word; word-break: break-all;"
      val imgClass = "col s7"
      val imgAttributes =
        "style=\"max-width: 100%; height: 100%; object-fit: cover;\" class=\"modal-trigger\""
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

        val contextData = if (image.contextData.isNotEmpty() && image.contextData.all {
            it.value.toString() != "null" && it.value.toString().isNotEmpty()
          }) {
          "<br>contextData:${image.contextData}"
        } else {
          ""
        }
        append("<td class=\"$fileNameClass\" style=\"$fileNameStyle\">${image.reportFile.name}$contextData</td>")
        append(
          "<td class=\"$imgClass\"><img $imgAttributes src=\"${
            image.reportFile.pathFrom(
              reportDirectoryPath
            )
          }\" data-alt=\"${image.reportFile.name}\" /></td>"
        )

        append("</tr>")
      }
      append("</tbody>")
      append("</table>")

    }
  }

  companion object {
    val gson: Gson = GsonBuilder()
      .registerTypeAdapter(File::class.java, object : JsonSerializer<File>, JsonDeserializer<File> {
        override fun serialize(
          src: File?,
          typeOfSrc: Type?,
          context: JsonSerializationContext?
        ): JsonElement {
          val absolutePath = src?.absolutePath ?: return JsonNull.INSTANCE
          return JsonPrimitive(absolutePath)
        }

        override fun deserialize(
          json: JsonElement?,
          typeOfT: Type?,
          context: JsonDeserializationContext?
        ): File {
          val path = json?.asString ?: throw JsonParseException("File path is null")
          return File(path)
        }
      })
      .create()

    fun fromJsonFile(inputPath: String): CaptureResults {
      val jsonObject = JsonParser.parseString(FileReader(inputPath).readText()).asJsonObject
      return fromJson(jsonObject)
    }

    fun fromJson(jsonObject: JsonObject): CaptureResults {
      // Auto convert using Gson
      return gson.fromJson(jsonObject, CaptureResults::class.java)
    }

    fun from(results: List<CaptureResult>): CaptureResults {
      return CaptureResults(
        resultSummary = ResultSummary(
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
