package com.github.takahirom.roborazzi

import android.util.JsonWriter
import java.io.File

sealed class CompareReportResult {
  abstract fun writeJson(writer: JsonWriter)

  abstract val timestamp: Long
  abstract val goldenFile: File

  data class Changed(
    val compareFile: File,
    override val goldenFile: File,
    override val timestamp: Long
  ) : CompareReportResult() {
    override fun writeJson(writer: JsonWriter) {
      writer.beginObject()
      writer.name("type").value("changed")
      writer.name("compare_file_path").value(compareFile.absolutePath)
      writer.name("golden_file_path").value(goldenFile.absolutePath)
      writer.name("timestamp").value(timestamp)
      writer.endObject()
    }
  }

  data class Unchanged(
    override val goldenFile: File,
    override val timestamp: Long
  ) : CompareReportResult() {
    override fun writeJson(writer: JsonWriter) {
      writer.beginObject()
      writer.name("type").value("unchanged")
      writer.name("golden_file_path").value(goldenFile.absolutePath)
      writer.name("timestamp").value(timestamp)
      writer.endObject()
    }
  }
}