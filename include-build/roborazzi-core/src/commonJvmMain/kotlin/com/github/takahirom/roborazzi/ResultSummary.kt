package com.github.takahirom.roborazzi

import org.json.JSONObject

data class ResultSummary(
  val total: Int,
  val recorded: Int,
  val added: Int,
  val changed: Int,
  val unchanged: Int
) {
  fun toJson(): JSONObject {
    val json = JSONObject()
    json.put("total", total)
    json.put("recorded", recorded)
    json.put("added", added)
    json.put("changed", changed)
    json.put("unchanged", unchanged)
    return json
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
    fun fromJson(jsonObject: JSONObject): ResultSummary {
      val total = jsonObject.getInt("total")
      val recorded = jsonObject.getInt("recorded")
      val added = jsonObject.getInt("added")
      val changed = jsonObject.getInt("changed")
      val unchanged = jsonObject.getInt("unchanged")
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