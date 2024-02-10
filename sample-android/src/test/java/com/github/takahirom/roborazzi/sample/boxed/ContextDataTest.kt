package com.github.takahirom.roborazzi.sample.boxed

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ashampoo.kim.format.png.PngChunkType
import com.ashampoo.kim.format.png.PngImageParser
import com.ashampoo.kim.format.png.chunk.PngTextChunk
import com.ashampoo.kim.input.KotlinIoSourceByteReader
import com.ashampoo.kim.input.use
import com.github.takahirom.roborazzi.CaptureResult
import com.github.takahirom.roborazzi.ROBORAZZI_DEBUG
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.provideRoborazziContext
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import com.github.takahirom.roborazzi.sample.MainActivity
import kotlinx.io.files.Path
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

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
      onView(ViewMatchers.isRoot())
        .captureRoboImage(
          roborazziOptions = provideRoborazziContext().options.copy(
            contextData = mapOf(
              "test_key" to "test_value"
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
          chunks.filterIsInstance<PngTextChunk>()
            .first { it.getKeyword() == "test_key" }
            .let { chunk ->
              assert(chunk.getText() == "test_value") {
                "Expected test_value but got ${chunk.getText()}"
              }
            }
        }
      }
      File("build/test-results/roborazzi/results")
        .listFiles()!!
        .first { it.name.contains(methodSignature) && it.name.endsWith(".json") }
        .let {
          CaptureResult.fromJsonFile(it.path)
            .let { result ->
              assert(result.contextData["test_key"] == "test_value") {
                "Expected test_value but got ${result.contextData["test_key"]}"
              }
            }
        }
    }
  }
}
