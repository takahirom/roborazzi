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