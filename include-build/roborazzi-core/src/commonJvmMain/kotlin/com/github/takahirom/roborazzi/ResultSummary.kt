package com.github.takahirom.roborazzi

import org.json.JSONObject

data class ResultSummary(
  val total: Int,
  val added: Int,
  val changed: Int,
  val unchanged: Int
) {
  fun toJson(): JSONObject {
    val json = JSONObject()
    json.put("total", total)
    json.put("added", added)
    json.put("changed", changed)
    json.put("unchanged", unchanged)
    return json
  }

  companion object {
    fun fromJson(jsonObject: JSONObject): ResultSummary {
      val total = jsonObject.getInt("total")
      val added = jsonObject.getInt("added")
      val changed = jsonObject.getInt("changed")
      val unchanged = jsonObject.getInt("unchanged")
      return ResultSummary(total, added, changed, unchanged)
    }
  }
}