package com.github.takahirom.roborazzi

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class ResultSummary(
  val total: Int,
  val recorded: Int,
  val added: Int,
  val changed: Int,
  val unchanged: Int
) {
  fun toJson(): JsonObject {
    return JsonParser.parseString(Gson().toJson(this)).asJsonObject
  }

  fun toHtml(): String {
    return """
        <h3>Summary</h3>
        <table class="highlight">
            <thead>
            <tr>
                <th>Total</th>
                <th><a href="#recorded">Recorded</a></th>
                <th><a href="#added">Added</a></th>
                <th><a href="#changed">Changed</a></th>
                <th><a href="#unchanged">Unchanged</a></th>
            </tr>
            </thead>
            <tbody>
            <tr>
                <td>$total</td>
                <td><a href="#recorded">$recorded</a></td>
                <td><a href="#added">$added</a></td>
                <td><a href="#changed">$changed</a></td>
                <td><a href="#unchanged">$unchanged</a></td>
            </tr>
            </tbody>
        </table>
    """.trimIndent()
  }

  companion object {
    fun fromJson(jsonObject: JsonObject): ResultSummary {
      val total = jsonObject.get("total").asInt
      val recorded = jsonObject.get("recorded").asInt
      val added = jsonObject.get("added").asInt
      val changed = jsonObject.get("changed").asInt
      val unchanged = jsonObject.get("unchanged").asInt
      return ResultSummary(
        total = total,
        recorded = recorded,
        added = added,
        changed = changed,
        unchanged = unchanged
      )
    }
  }
}