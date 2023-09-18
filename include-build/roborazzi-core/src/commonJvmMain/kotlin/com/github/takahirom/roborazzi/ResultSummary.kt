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
                <th>Recorded</th>
                <th>Added</th>
                <th>Changed</th>
                <th>Unchanged</th>
            </tr>
            </thead>
            <tbody>
            <tr>
                <td>$total</td>
                <td>$recorded</td>
                <td>$added</td>
                <td>$changed</td>
                <td>$unchanged</td>
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