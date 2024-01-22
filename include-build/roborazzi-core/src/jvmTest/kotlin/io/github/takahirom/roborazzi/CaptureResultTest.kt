package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.CaptureResult
import com.github.takahirom.roborazzi.CaptureResults
import com.github.takahirom.roborazzi.ResultSummary
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class CaptureResultTest {

  @Test
  fun testJsonSerialization() {
    val summary = ResultSummary(11, 1, 2, 3, 5)
    val captureResults = listOf(
      CaptureResult.Recorded(
        goldenFile = File("/golden_file"),
        timestampNs = 123456789,
      ),
      CaptureResult.Added(
        compareFile = File("/compare_file"),
        actualFile = File("/actual_file"),
        goldenFile = File("/golden_file"),
        timestampNs = 123456789,
      ),
      CaptureResult.Changed(
        compareFile = File("/compare_file"),
        goldenFile = File("/golden_file"),
        actualFile = File("/actual_file"),
        timestampNs = 123456789,
      ),
      CaptureResult.Unchanged(
        goldenFile = File("/golden_file"),
        timestampNs = 123456789
      )
    )

    val compareReportResult = CaptureResults(summary, captureResults)

    val json = compareReportResult.toJson()
    val jsonSummary = json.get("summary").asJsonObject
    val jsonResults = json.get("results").asJsonArray

    // Test summary
    assertEquals(summary.total, jsonSummary.get("total").asInt)
    assertEquals(summary.recorded, jsonSummary.get("recorded").asInt)
    assertEquals(summary.added, jsonSummary.get("added").asInt)
    assertEquals(summary.changed, jsonSummary.get("changed").asInt)
    assertEquals(summary.unchanged, jsonSummary.get("unchanged").asInt)

    // Test capture results
    assertEquals(captureResults.size, jsonResults.size())

    for (i in 0 until jsonResults.size()) {
      val jsonResult = jsonResults.get(i).asJsonObject
      val captureResult = captureResults[i]

      assertEquals(
        captureResult.compareFile?.absolutePath, jsonResult.get("compare_file_path")?.asString
      )
      assertEquals(
        captureResult.goldenFile?.absolutePath,
        jsonResult.get("golden_file_path")?.asString
      )
      assertEquals(
        captureResult.actualFile?.absolutePath,
        jsonResult.get("actual_file_path")?.asString
      )
      assertEquals(captureResult.timestampNs, jsonResult.get("timestamp").asLong)
    }
  }

  @Test
  fun testJsonDeserialization() {
    val jsonString = """
        {
            "summary": {
                "total": 11,
                "recorded": 1,
                "added": 2,
                "changed": 3,
                "unchanged": 5
            },
            "results": [
                {
                    "type": "recorded",
                    "golden_file_path": "golden_file",
                    "timestamp": 123456789
                },
                {
                    "type": "added",
                    "compare_file_path": "compare_file",
                    "actual_file_path": "actual_file",
                    "golden_file_path": "golden_file",
                    "timestamp": 123456789
                },
                {
                    "type": "changed",
                    "compare_file_path": "compare_file",
                    "actual_file_path": "actual_file",
                    "golden_file_path": "golden_file",
                    "timestamp": 123456789
                },
                {
                    "type": "unchanged",
                    "golden_file_path": "golden_file",
                    "timestamp": 123456789
                }
            ]
        }
        """.trimIndent()
    val jsonObject = JsonParser.parseString(jsonString).asJsonObject
    val compareReportResult = CaptureResults.fromJson(jsonObject)
    val summary = compareReportResult.resultSummary
    val captureResults = compareReportResult.captureResults

    // Test summary
    assertEquals(11, summary.total)
    assertEquals(1, summary.recorded)
    assertEquals(2, summary.added)
    assertEquals(3, summary.changed)
    assertEquals(5, summary.unchanged)

    // Test capture results
    assertEquals(4, captureResults.size)

    val recordedResult = captureResults[0] as CaptureResult.Recorded
    assertEquals(File("golden_file"), recordedResult.goldenFile)
    assertEquals(123456789, recordedResult.timestampNs)

    val addedResult = captureResults[1] as CaptureResult.Added
    assertEquals(File("compare_file"), addedResult.compareFile)
    assertEquals(File("actual_file"), addedResult.actualFile)
    assertEquals(123456789, addedResult.timestampNs)

    val changedResult = captureResults[2] as CaptureResult.Changed
    assertEquals(File("compare_file"), changedResult.compareFile)
    assertEquals(File("actual_file"), changedResult.actualFile)
    assertEquals(File("golden_file"), changedResult.goldenFile)
    assertEquals(123456789, changedResult.timestampNs)

    val unchangedResult = captureResults[3] as CaptureResult.Unchanged
    assertEquals(File("golden_file"), unchangedResult.goldenFile)
    assertEquals(123456789, unchangedResult.timestampNs)
  }
}
