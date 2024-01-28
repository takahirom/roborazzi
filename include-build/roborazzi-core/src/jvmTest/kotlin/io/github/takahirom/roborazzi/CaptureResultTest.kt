package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.CaptureResult
import com.github.takahirom.roborazzi.CaptureResults
import com.github.takahirom.roborazzi.CaptureResults.Companion.gson
import com.github.takahirom.roborazzi.ResultSummary
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class CaptureResultTest {

  @Test
  fun testJsonSerialization() {
    val expectedSummary = ResultSummary(11, 1, 2, 3, 5)
    val expectedCaptureResults = listOf(
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

    val expectedReportResults = CaptureResults(expectedSummary, expectedCaptureResults)

    val actualJson = gson.toJsonTree(expectedReportResults).asJsonObject
    val actualJsonSummary = actualJson.get("summary").asJsonObject
    val actualJsonResults = actualJson.get("results").asJsonArray

    // Test summary
    assertEquals(expectedSummary.total, actualJsonSummary.get("total").asInt)
    assertEquals(expectedSummary.recorded, actualJsonSummary.get("recorded").asInt)
    assertEquals(expectedSummary.added, actualJsonSummary.get("added").asInt)
    assertEquals(expectedSummary.changed, actualJsonSummary.get("changed").asInt)
    assertEquals(expectedSummary.unchanged, actualJsonSummary.get("unchanged").asInt)

    // Test capture results
    assertEquals(expectedCaptureResults.size, actualJsonResults.size())

    for (i in 0 until actualJsonResults.size()) {
      val actualJsonResult = actualJsonResults.get(i).asJsonObject
      val expectedCaptureResult = expectedCaptureResults[i]

      assertEquals(
        expectedCaptureResult.type,
        actualJsonResult.get("type")?.asString
      )

      assertEquals(
        expectedCaptureResult.compareFile?.absolutePath, actualJsonResult.get("compare_file_path")?.asString
      )
      assertEquals(
        expectedCaptureResult.goldenFile?.absolutePath,
        actualJsonResult.get("golden_file_path")?.asString
      )
      assertEquals(
        expectedCaptureResult.actualFile?.absolutePath,
        actualJsonResult.get("actual_file_path")?.asString
      )
      assertEquals(expectedCaptureResult.timestampNs, actualJsonResult.get("timestamp").asLong)
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
    val actualJsonObject = JsonParser.parseString(jsonString).asJsonObject
    val actualCaptureResults= CaptureResults.fromJson(actualJsonObject)
    val actualSummary = actualCaptureResults.resultSummary
    val actualCaptureResultList = actualCaptureResults.captureResults

    // Test summary
    assertEquals(11, actualSummary.total)
    assertEquals(1, actualSummary.recorded)
    assertEquals(2, actualSummary.added)
    assertEquals(3, actualSummary.changed)
    assertEquals(5, actualSummary.unchanged)

    // Test capture results
    assertEquals(4, actualCaptureResultList.size)

    val actualRecordedResult = actualCaptureResultList[0] as CaptureResult.Recorded
    assertEquals(File("golden_file"), actualRecordedResult.goldenFile)
    assertEquals(123456789, actualRecordedResult.timestampNs)

    val actualAddedResult = actualCaptureResultList[1] as CaptureResult.Added
    assertEquals(File("compare_file"), actualAddedResult.compareFile)
    assertEquals(File("actual_file"), actualAddedResult.actualFile)
    assertEquals(123456789, actualAddedResult.timestampNs)

    val actualChangedResult = actualCaptureResultList[2] as CaptureResult.Changed
    assertEquals(File("compare_file"), actualChangedResult.compareFile)
    assertEquals(File("actual_file"), actualChangedResult.actualFile)
    assertEquals(File("golden_file"), actualChangedResult.goldenFile)
    assertEquals(123456789, actualChangedResult.timestampNs)

    val actualUnchangedResult = actualCaptureResultList[3] as CaptureResult.Unchanged
    assertEquals(File("golden_file"), actualUnchangedResult.goldenFile)
    assertEquals(123456789, actualUnchangedResult.timestampNs)
  }
}
