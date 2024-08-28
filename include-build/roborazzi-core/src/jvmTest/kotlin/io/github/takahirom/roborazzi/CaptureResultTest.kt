package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.CaptureResult
import com.github.takahirom.roborazzi.CaptureResults
import com.github.takahirom.roborazzi.CaptureResults.Companion.json
import com.github.takahirom.roborazzi.ResultSummary
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureResultTest {

  @Test
  fun testJsonSerialization() {
    val expectedSummary = ResultSummary(11, 1, 2, 3, 5)
    val expectedCaptureResults = listOf(
      CaptureResult.Recorded(
        goldenFile = "/golden_file",
        timestampNs = 123456789,
        contextData = mapOf("key" to "value1"),
      ),
      CaptureResult.Added(
        compareFile = "/compare_file",
        actualFile = "/actual_file",
        goldenFile = "/golden_file",
        timestampNs = 123456789,
        contextData = mapOf(
          "key" to 2,
          "keyDouble" to 2.5,
        ),
      ),
      CaptureResult.Changed(
        compareFile = "/compare_file",
        goldenFile = "/golden_file",
        actualFile = "/actual_file",
        timestampNs = 123456789,
        diffPercentage = 0.123f,
        contextData = mapOf("key" to Long.MAX_VALUE - 100),
      ),
      CaptureResult.Unchanged(
        goldenFile = "/golden_file",
        timestampNs = 123456789,
        contextData = mapOf("key" to true),
      )
    )

    val expectedReportResults = CaptureResults(expectedSummary, expectedCaptureResults)

    val actualJson = json.encodeToJsonElement(expectedReportResults) as JsonObject
    val actualJsonSummary = actualJson["summary"]!!.jsonObject
    val actualJsonResults = actualJson["results"]!!.jsonArray

    // Test summary
    assertEquals(expectedSummary.total, actualJsonSummary["total"]!!.jsonPrimitive.int)
    assertEquals(expectedSummary.recorded, actualJsonSummary["recorded"]!!.jsonPrimitive.int)
    assertEquals(expectedSummary.added, actualJsonSummary["added"]!!.jsonPrimitive.int)
    assertEquals(expectedSummary.changed, actualJsonSummary["changed"]!!.jsonPrimitive.int)
    assertEquals(expectedSummary.unchanged, actualJsonSummary["unchanged"]!!.jsonPrimitive.int)

    // Test capture results
    assertEquals(expectedCaptureResults.size, actualJsonResults.size)
    for (i in 0 until actualJsonResults.size) {
      val actualJsonResult = actualJsonResults[i].jsonObject
      val expectedCaptureResult = expectedCaptureResults[i]
      assertEquals(
        expectedCaptureResult.type,
        actualJsonResult["type"]?.jsonPrimitive?.content
      )

      assertEquals(
        expectedCaptureResult.compareFile,
        actualJsonResult["compare_file_path"]?.jsonPrimitive?.content
      )
      assertEquals(
        expectedCaptureResult.goldenFile,
        actualJsonResult["golden_file_path"]?.jsonPrimitive?.content
      )
      assertEquals(
        expectedCaptureResult.actualFile,
        actualJsonResult["actual_file_path"]?.jsonPrimitive?.content
      )
      assertEquals(
        expectedCaptureResult.timestampNs,
        actualJsonResult["timestamp"]?.jsonPrimitive?.long
      )
      if (expectedCaptureResult is CaptureResult.Changed) {
        assertEquals(
          expectedCaptureResult.diffPercentage,
          actualJsonResult["diff_percentage"]?.jsonPrimitive?.float
        )
      }
      assertEquals(
        expectedCaptureResult.contextData.entries.map { it.key to it.value },
        (actualJsonResult["context_data"]?.jsonObject?.entries
          ?.associate {
            it.key to when (expectedCaptureResult.contextData[it.key]) {
              is Number -> when {
                it.value.jsonPrimitive.intOrNull != null -> it.value.jsonPrimitive.int
                it.value.jsonPrimitive.longOrNull != null -> it.value.jsonPrimitive.long
                it.value.jsonPrimitive.doubleOrNull != null -> it.value.jsonPrimitive.double
                else -> error("Unsupported type")
              }

              is String -> it.value.jsonPrimitive.content
              is Boolean -> it.value.jsonPrimitive.boolean
              else -> error("Unsupported type")
            }
          }
          ?.entries as Set<Map.Entry<String, Any>>
          ).map { it.key to it.value })
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
                    "timestamp": 123456789,
                    "context_data": {
                        "key": "value1"
                    }
                },
                {
                    "type": "added",
                    "compare_file_path": "compare_file",
                    "actual_file_path": "actual_file",
                    "golden_file_path": "golden_file",
                    "timestamp": 123456789,
                    "context_data": {
                        "key": 2,
                        "keyDouble": 2.5
                    }
                },
                {
                    "type": "changed",
                    "compare_file_path": "compare_file",
                    "actual_file_path": "actual_file",
                    "golden_file_path": "golden_file",
                    "timestamp": 123456789,
                    "diff_percentage": 0.123,
                    "context_data": {
                        "key": 9223372036854775707
                    }
                },
                {
                    "type": "unchanged",
                    "golden_file_path": "golden_file",
                    "timestamp": 123456789,
                    "context_data": {
                        "key": true
                    }
                }
            ]
        }
        """.trimIndent()
    val actualJsonObject = json.parseToJsonElement(jsonString).jsonObject
    val actualCaptureResults = CaptureResults.fromJson(actualJsonObject)
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
    assertEquals("golden_file", actualRecordedResult.goldenFile)
    assertEquals(123456789, actualRecordedResult.timestampNs)
    assertEquals("value1", actualRecordedResult.contextData["key"])

    val actualAddedResult = actualCaptureResultList[1] as CaptureResult.Added
    assertEquals("compare_file", actualAddedResult.compareFile)
    assertEquals("actual_file", actualAddedResult.actualFile)
    assertEquals(123456789, actualAddedResult.timestampNs)
    assertEquals(2, actualAddedResult.contextData["key"])
    assertEquals(2.5, actualAddedResult.contextData["keyDouble"])

    val actualChangedResult = actualCaptureResultList[2] as CaptureResult.Changed
    assertEquals("compare_file", actualChangedResult.compareFile)
    assertEquals("actual_file", actualChangedResult.actualFile)
    assertEquals("golden_file", actualChangedResult.goldenFile)
    assertEquals(123456789, actualChangedResult.timestampNs)
    assertEquals(0.123f, actualChangedResult.diffPercentage)
    assertEquals(9223372036854775707, actualChangedResult.contextData["key"])

    val actualUnchangedResult = actualCaptureResultList[3] as CaptureResult.Unchanged
    assertEquals("golden_file", actualUnchangedResult.goldenFile)
    assertEquals(123456789, actualUnchangedResult.timestampNs)
    assertEquals(true, actualUnchangedResult.contextData["key"])
  }
}
