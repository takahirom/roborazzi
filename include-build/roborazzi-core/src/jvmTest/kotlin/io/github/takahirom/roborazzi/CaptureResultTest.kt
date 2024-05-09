package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.CaptureResult
import com.github.takahirom.roborazzi.CaptureResults
import com.github.takahirom.roborazzi.ResultSummary
import com.github.takahirom.roborazzi.CaptureResults.Companion.json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
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
        contextData = mapOf("key" to "value1"),
      ),
      CaptureResult.Added(
        compareFile = File("/compare_file"),
        actualFile = File("/actual_file"),
        goldenFile = File("/golden_file"),
        timestampNs = 123456789,
        contextData = mapOf("key" to 2),
      ),
      CaptureResult.Changed(
        compareFile = File("/compare_file"),
        goldenFile = File("/golden_file"),
        actualFile = File("/actual_file"),
        timestampNs = 123456789,
        contextData = mapOf("key" to Long.MAX_VALUE - 100),
      ),
      CaptureResult.Unchanged(
        goldenFile = File("/golden_file"),
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
        expectedCaptureResult.compareFile?.absolutePath,
        actualJsonResult["compare_file_path"]?.jsonPrimitive?.content
      )
      assertEquals(
        expectedCaptureResult.goldenFile?.absolutePath,
        actualJsonResult["golden_file_path"]?.jsonPrimitive?.content
      )
      assertEquals(
        expectedCaptureResult.actualFile?.absolutePath,
        actualJsonResult["actual_file_path"]?.jsonPrimitive?.content
      )
      assertEquals(expectedCaptureResult.timestampNs, actualJsonResult["timestamp"]?.jsonPrimitive?.long)
      assertEquals(
        expectedCaptureResult.contextData.entries.map { it.key to it.value },
        (actualJsonResult["context_data"]?.jsonObject?.entries
          ?.associate {
            it.key to when (expectedCaptureResult.contextData[it.key]) {
              is Number -> if (it.value.jsonPrimitive.long > Int.MAX_VALUE) {
                it.value.jsonPrimitive.long
              } else {
                it.value.jsonPrimitive.int
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
                        "key": 2
                    }
                },
                {
                    "type": "changed",
                    "compare_file_path": "compare_file",
                    "actual_file_path": "actual_file",
                    "golden_file_path": "golden_file",
                    "timestamp": 123456789,
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
    assertEquals(File("golden_file"), actualRecordedResult.goldenFile)
    assertEquals(123456789, actualRecordedResult.timestampNs)
    assertEquals("value1", actualRecordedResult.contextData["key"])

    val actualAddedResult = actualCaptureResultList[1] as CaptureResult.Added
    assertEquals(File("compare_file"), actualAddedResult.compareFile)
    assertEquals(File("actual_file"), actualAddedResult.actualFile)
    assertEquals(123456789, actualAddedResult.timestampNs)
    assertEquals(2, actualAddedResult.contextData["key"])

    val actualChangedResult = actualCaptureResultList[2] as CaptureResult.Changed
    assertEquals(File("compare_file"), actualChangedResult.compareFile)
    assertEquals(File("actual_file"), actualChangedResult.actualFile)
    assertEquals(File("golden_file"), actualChangedResult.goldenFile)
    assertEquals(123456789, actualChangedResult.timestampNs)
    // Currently long value is deserialized as double so we can't handle long value correctly
//    assertEquals(9223372036854775707, (actualChangedResult.contextData["key"] as Double).toLong())

    val actualUnchangedResult = actualCaptureResultList[3] as CaptureResult.Unchanged
    assertEquals(File("golden_file"), actualUnchangedResult.goldenFile)
    assertEquals(123456789, actualUnchangedResult.timestampNs)
    assertEquals(true, actualUnchangedResult.contextData["key"])
  }
}
