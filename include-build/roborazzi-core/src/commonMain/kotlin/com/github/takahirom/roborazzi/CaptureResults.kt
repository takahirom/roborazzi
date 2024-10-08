package com.github.takahirom.roborazzi

import kotlinx.io.files.Path
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.modules.SerializersModule

@Serializable
data class CaptureResults(
  @SerialName("summary")
  val resultSummary: ResultSummary,
  @SerialName("results")
  val captureResults: List<CaptureResult>
) {

  fun toJson(): String {
    return json.encodeToString(this)
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
              .toSortedMap { value1, value2 ->
                // show "undefined" and "null" at the end
                if (value1 == "undefined" || value2 == "null") {
                  1
                } else if (value1 == "null" || value2 == "undefined") {
                  -1
                } else {
                  value1.compareTo(value2)
                }
              }
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
      tabs.forEachIndexed { tabIndex, tab ->
        // <li class="tab col s3"><a href="#test1">Test 1</a></li>
        val activeClass = if (tabIndex == 0) """class="active" """ else ""
        append("""<li class="tab"><a $activeClass href="#${tab.id}">${tab.title}</a></li>""")
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
    fun String.pathFrom(reportDirectoryPath: String): String {
      val reportDirectory = Path(reportDirectoryPath)
      val relativePath = Path(this).relativeTo(reportDirectory)
      return relativePath.toString()
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
    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
      isLenient = true
      encodeDefaults = true
      ignoreUnknownKeys = true
      classDiscriminator = "#class"
      explicitNulls = false
      serializersModule = SerializersModule {
        contextual(Any::class, AnySerializer)
      }
    }

    fun fromJsonFile(inputPath: String): CaptureResults {
      val string = KotlinxIo.readText(inputPath)
      val jsonElement = json.parseToJsonElement(string)
      return json.decodeFromJsonElement(jsonElement)
    }

    fun fromJson(jsonString: JsonObject): CaptureResults {
      return json.decodeFromJsonElement(jsonString)
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

object AnySerializer : KSerializer<Any> {
  @OptIn(ExperimentalSerializationApi::class)
  override val descriptor: SerialDescriptor
    get() = ContextualSerializer(Any::class, null, emptyArray()).descriptor

  private val delegateSerializer = JsonPrimitive.serializer()

  override fun serialize(encoder: Encoder, value: Any) {
    when (value) {
      is String -> encoder.encodeString(value)
      is Int -> encoder.encodeInt(value)
      is Long -> encoder.encodeLong(value)
      is Double -> encoder.encodeDouble(value.toDouble())
      is Boolean -> encoder.encodeBoolean(value)
      else -> throw IllegalArgumentException("Unknown type: ${value::class.qualifiedName}")
    }
  }

  override fun deserialize(decoder: Decoder): Any {
    val input = decoder.decodeSerializableValue(delegateSerializer)
    return when {
      input.isString -> input.content
      input.booleanOrNull != null -> input.boolean
      input.intOrNull != null -> input.int
      input.longOrNull != null -> input.long
      input.doubleOrNull != null -> input.double
      else -> throw IllegalArgumentException("Unknown type: ${input::class.qualifiedName}")
    }
  }
}

fun <K : Comparable<K>, V> Map<out K, V>.toSortedMap(comparator: Comparator<in K>): Map<K, V> {
  return this.toList().sortedWith(compareBy(comparator) { it.first }).toMap()
}