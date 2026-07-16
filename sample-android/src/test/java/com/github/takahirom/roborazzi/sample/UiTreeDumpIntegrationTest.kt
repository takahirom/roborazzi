package com.github.takahirom.roborazzi.sample

import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.UiTreeDumpOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel4, sdk = [35])
class UiTreeDumpIntegrationTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun writesUiTreeSidecarNextToImage() {
    composeTestRule.setContent {
      Column {
        Text(
          text = "Login",
          modifier = Modifier
            .testTag("login_button")
            .size(120.dp)
            .clickable { }
        )
      }
    }

    val prefix = "${roborazziSystemPropertyOutputDirectory()}/${this::class.qualifiedName}.uiTreeDump"
    val imageFile = File("$prefix.png")
    val sidecarFile = File("$prefix.uitree.json")
    imageFile.delete()
    sidecarFile.delete()

    onView(isRoot()).captureRoboImage(
      file = imageFile,
      roborazziOptions = RoborazziOptions(
        taskType = RoborazziTaskType.Record,
        uiTreeDumpOptions = UiTreeDumpOptions(),
      ),
    )

    // The sidecar exists next to the png.
    assertTrue(
      "sidecar not found: ${sidecarFile.absolutePath}",
      sidecarFile.exists()
    )

    val json = sidecarFile.readText()

    // It is valid JSON (org.json is available under Robolectric).
    JSONObject(json)

    // Grep-ability: exactly one line contains the testTag, and that same line
    // also carries the node's bounds.
    val tagLines = json.lines().filter { it.contains("\"login_button\"") }
    assertEquals("expected exactly one line with the testTag:\n$json", 1, tagLines.size)
    assertTrue(
      "the testTag line must also contain bounds:\n${tagLines.single()}",
      tagLines.single().contains("\"bounds\": [")
    )

    // The first annotatable node is numbered.
    assertTrue("expected \"n\": 1 in:\n$json", json.contains("\"n\": 1"))

    imageFile.delete()
    sidecarFile.delete()
  }

  @Test
  fun writesAnnotatedImageMatchingSidecarNumbering() {
    composeTestRule.setContent {
      Column {
        Text(
          text = "Login",
          modifier = Modifier
            .testTag("login_button")
            .size(120.dp)
            .clickable { }
        )
        Text(text = "Forgot password?")
      }
    }

    val prefix =
      "${roborazziSystemPropertyOutputDirectory()}/${this::class.qualifiedName}.annotated"
    val imageFile = File("$prefix.png")
    val sidecarFile = File("$prefix.uitree.json")
    val annotatedFile = File("$prefix.annotated.png")
    listOf(imageFile, sidecarFile, annotatedFile).forEach { it.delete() }

    onView(isRoot()).captureRoboImage(
      file = imageFile,
      roborazziOptions = RoborazziOptions(
        taskType = RoborazziTaskType.Record,
        uiTreeDumpOptions = UiTreeDumpOptions(),
      ),
    )

    // The annotated image is written next to the screenshot.
    assertTrue("annotated image not found: ${annotatedFile.absolutePath}", annotatedFile.exists())

    val screenshot = BitmapFactory.decodeFile(imageFile.absolutePath)
    val annotated = BitmapFactory.decodeFile(annotatedFile.absolutePath)

    // Same dimensions as the screenshot.
    assertEquals(screenshot.width, annotated.width)
    assertEquals(screenshot.height, annotated.height)

    // The boxes were drawn, so at least some pixels differ from the screenshot.
    var differingPixels = 0
    outer@ for (x in 0 until screenshot.width) {
      for (y in 0 until screenshot.height) {
        if (screenshot.getPixel(x, y) != annotated.getPixel(x, y)) {
          differingPixels++
          if (differingPixels > 0) break@outer
        }
      }
    }
    assertTrue("annotated image is identical to the screenshot", differingPixels > 0)

    // Numbering consistency: the max n drawn equals the max n in the sidecar JSON.
    val json = sidecarFile.readText()
    val maxNInJson = Regex("\"n\": (\\d+)").findAll(json)
      .map { it.groupValues[1].toInt() }
      .maxOrNull()
    // Two annotatable nodes here (login_button, "Forgot password?").
    assertEquals(2, maxNInJson)

    listOf(imageFile, sidecarFile, annotatedFile).forEach { it.delete() }
  }

  @Test
  fun annotateImageFalseWritesSidecarButNoAnnotatedImage() {
    composeTestRule.setContent {
      Text(
        text = "Login",
        modifier = Modifier
          .testTag("login_button")
          .size(120.dp)
      )
    }

    val prefix =
      "${roborazziSystemPropertyOutputDirectory()}/${this::class.qualifiedName}.optOut"
    val imageFile = File("$prefix.png")
    val sidecarFile = File("$prefix.uitree.json")
    val annotatedFile = File("$prefix.annotated.png")
    listOf(imageFile, sidecarFile, annotatedFile).forEach { it.delete() }

    onView(isRoot()).captureRoboImage(
      file = imageFile,
      roborazziOptions = RoborazziOptions(
        taskType = RoborazziTaskType.Record,
        uiTreeDumpOptions = UiTreeDumpOptions(annotateImage = false),
      ),
    )

    assertTrue("sidecar should still be written", sidecarFile.exists())
    assertFalse("no annotated image when annotateImage = false", annotatedFile.exists())

    listOf(imageFile, sidecarFile, annotatedFile).forEach { it.delete() }
  }
}
