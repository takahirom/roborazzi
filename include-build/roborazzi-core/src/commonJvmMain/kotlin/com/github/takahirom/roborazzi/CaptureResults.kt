package com.github.takahirom.roborazzi

import com.google.gson.*
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
      append(resultSummary.toHtml())
      append(buildTable("Recorded images", "recorded", recordedImages))
      append(buildTable("Added images", "added", addedImages))
      append(buildTable("Changed images", "changed", changedImages))
      append(buildTable("Unchanged images", "unchanged", unchangedImages))
    }
  }

  companion object {
    val gson: Gson = GsonBuilder()
      .registerTypeAdapter(File::class.java, object : JsonSerializer<File>, JsonDeserializer<File> {
        override fun serialize(src: File?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
          val absolutePath = src?.absolutePath ?: return JsonNull.INSTANCE
          return JsonPrimitive(absolutePath)
        }

        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): File {
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
