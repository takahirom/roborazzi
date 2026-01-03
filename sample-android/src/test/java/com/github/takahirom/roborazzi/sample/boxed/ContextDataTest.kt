package com.github.takahirom.roborazzi.sample.boxed

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ashampoo.kim.format.png.PngChunkType
import com.ashampoo.kim.format.png.PngImageParser
import com.ashampoo.kim.format.png.chunk.PngChunk
import com.ashampoo.kim.format.png.chunk.PngTextChunk
import com.ashampoo.kim.input.KotlinIoSourceByteReader
import com.ashampoo.kim.input.use
import com.github.takahirom.roborazzi.CaptureResult
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.ROBORAZZI_DEBUG
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.provideRoborazziContext
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.github.takahirom.roborazzi.roborazziSystemPropertyResultDirectory
import com.github.takahirom.roborazzi.sample.MainActivity
import kotlinx.io.files.Path
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
  sdk = [30],
  qualifiers = RobolectricDeviceQualifiers.NexusOne
)
class ContextDataTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun canWriteImageContextData() {
    boxedEnvironment {
      ROBORAZZI_DEBUG = true
      val methodSignature =
        "${this::class.qualifiedName}.canWriteImageContextData"
      val expectedOutput =
        File("${roborazziSystemPropertyOutputDirectory()}/$methodSignature.png")
      expectedOutput.delete()
      System.setProperty(
        "roborazzi.test.record",
        "true"
      )
      val testKey1 = "test_key"
      val testValue1 = "test_value"
      val testKey2 = "test_key2"
      val testKey3 = "test_key3_double"
      val testKey4 = "test_key3_long"
      val testValue2 = 10
      val testValue3 = 5.5
      val testValue4 = Long.MAX_VALUE - 100
      onView(ViewMatchers.isRoot())
        .captureRoboImage(
          roborazziOptions = provideRoborazziContext().options.copy(
            contextData = mapOf(
              testKey1 to testValue1,
              testKey2 to testValue2,
              testKey3 to testValue3,
              testKey4 to testValue4,
            )
          )
        )

      assert(
        expectedOutput
          .exists()
      ) {
        "File not found: ${expectedOutput.absolutePath} \n"
      }
      KotlinIoSourceByteReader.read(Path(expectedOutput.absolutePath)) { byteReader ->
        byteReader?.use {
          val chunks = PngImageParser.readChunks(it, listOf(PngChunkType.TEXT))
          chunks.verifyKeyValueExistsInImage(testKey1, testValue1)
          chunks.verifyKeyValueExistsInImage(testKey2, testValue2.toString())
          chunks.verifyKeyValueExistsInImage(testKey3, testValue3.toString())
          chunks.verifyKeyValueExistsInImage(testKey4, testValue4.toString())
        }
      }
      File(roborazziSystemPropertyResultDirectory())
        .listFiles()!!
        .sortedBy { it.name }
        .reversed()
        .first { it.name.contains(methodSignature) && it.name.endsWith(".json") }
        .let {
          println(it.readText())
          CaptureResult.fromJsonFile(it.path)
            .let { result ->
              result.verifyKeyValueExistsInJson(testKey1, testValue1)
              result.verifyKeyValueExistsInJson(testKey2, testValue2)
              result.verifyKeyValueExistsInJson(testKey3, testValue3)
              result.verifyKeyValueExistsInJson(testKey4, testValue4)
            }
        }
    }
  }

  @Test
  fun disableContextTest() {
    boxedEnvironment {
      ROBORAZZI_DEBUG = true
      val methodSignature =
        "${this::class.qualifiedName}.disableContextTest"
      val expectedOutput =
        File("${roborazziSystemPropertyOutputDirectory()}/$methodSignature.png")
      expectedOutput.delete()
      System.setProperty(
        "roborazzi.test.record",
        "true"
      )
      System.setProperty(
        "roborazzi.contextdata",
        "false"
      )
      val testKey1 = "test_key"
      val testValue1 = "test_value"
      onView(ViewMatchers.isRoot())
        .captureRoboImage(
          roborazziOptions = provideRoborazziContext().options.copy(
            contextData = mapOf(
              testKey1 to testValue1
            )
          )
        )

      assert(
        expectedOutput
          .exists()
      ) {
        "File not found: ${expectedOutput.absolutePath} \n"
      }
      KotlinIoSourceByteReader.read(Path(expectedOutput.absolutePath)) { byteReader ->
        byteReader?.use {
          val chunks = PngImageParser.readChunks(it, listOf(PngChunkType.TEXT))
          assert(chunks.isEmpty()) {
            "Expected no chunks but got $chunks"
          }
        }
      }
      File(roborazziSystemPropertyResultDirectory())
        .listFiles()!!
        .first { it.name.contains(methodSignature) && it.name.endsWith(".json") }
        .let {
          CaptureResult.fromJsonFile(it.path)
            .let { result ->
              assert(result.contextData.isEmpty()) {
                "Expected no context data but got ${result.contextData}"
              }
            }
        }
    }
  }

  private fun CaptureResult.verifyKeyValueExistsInJson(
    key: String,
    value: Any
  ) {
    val any = contextData[key]
    assert(any == value) {
      "Expected $value but got $any"
    }
  }

  private fun List<PngChunk>.verifyKeyValueExistsInImage(
    key: String,
    value: String
  ) {
    filterIsInstance<PngTextChunk>()
      .first { it.getKeyword() == key }
      .let { chunk ->
        assert(chunk.getText() == value) {
          "Expected $value but got ${chunk.getText()}"
        }
      }
  }
}
