package com.github.takahirom.roborazzi.sample

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.AiAssertionOptions
import com.github.takahirom.roborazzi.DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.OpenAiAiAssertionModel
import com.github.takahirom.roborazzi.ROBORAZZI_DEBUG
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.provideRoborazziContext
import com.github.takahirom.roborazzi.roboOutputName
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
  sdk = [30],
  qualifiers = RobolectricDeviceQualifiers.NexusOne
)
class OllamaWithOpenAiApiInterfaceTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @get:Rule
  val roborazziRule = RoborazziRule(
    options = RoborazziRule.Options(
      roborazziOptions = RoborazziOptions(
        taskType =  RoborazziTaskType.Compare,
        compareOptions = RoborazziOptions.CompareOptions(
          aiAssertionOptions = AiAssertionOptions(
            assertionImageType = AiAssertionOptions.AssertionImageType.Actual(),
            aiAssertionModel = OpenAiAiAssertionModel(
//              loggingEnabled = true,
              baseUrl = "http://localhost:11434/v1/",
              apiKey = "",
              apiType = OpenAiAiAssertionModel.ApiType.Ollama,
              modelName = "gemma3:12b",
              seed = null,
            ),
          )
        )
      )
    )
  )

  fun isOllamaRunning(): Boolean {
    val conn: URLConnection?
    return try {
      conn = URL("http://localhost:11434").openConnection() as HttpURLConnection
      conn.requestMethod = "GET"
      conn.connectTimeout = 500
      conn.readTimeout = 500
      conn.responseCode == 200 && conn.inputStream.bufferedReader().use { it.readText().trim() } == "Ollama is running"
    } catch (e: Exception) {
      false
    } finally {
    }
  }

  @Test
  fun captureWithAi() {
    if (!isOllamaRunning()) {
      // Check if Ollama is running
      println("Ollama is not running. Please start Ollama and try again.")
      return
    }
    ROBORAZZI_DEBUG = true
    File(DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH + File.separator + roboOutputName() + ".png").delete()
    onView(ViewMatchers.isRoot())
      .captureRoboImage(
        roborazziOptions = provideRoborazziContext().options.addedAiAssertions(
          AiAssertionOptions.AiAssertion(
            assertionPrompt = "it should have PREVIOUS button",
            requiredFulfillmentPercent = 90,
          ),
          AiAssertionOptions.AiAssertion(
            assertionPrompt = "it should show First Fragment",
            requiredFulfillmentPercent = 90,
          )
        )
      )
    File(DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH + File.separator + roboOutputName() + "_compare.png").delete()
  }
}