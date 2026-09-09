package com.github.takahirom.roborazzi.sample

import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
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
  fun customActionsAreSerializedWithoutLambdaRuntimeIdentity() {
    composeTestRule.setContent {
      Text(
        text = "Login",
        modifier = Modifier
          .testTag("login_button")
          .semantics {
            // Reverse-alphabetical declaration order so the assertion below
            // actually exercises the deterministic sorting.
            customActions = listOf(
              CustomAccessibilityAction("Second action") { true },
              CustomAccessibilityAction("More actions") { true },
            )
          }
      )
    }

    val prefix =
      "${roborazziSystemPropertyOutputDirectory()}/${this::class.qualifiedName}.customActions"
    val imageFile = File("$prefix.png")
    val sidecarFile = File("$prefix.uitree.json")
    val annotatedFile = File("$prefix.annotated.png")
    val secondImageFile = File("${prefix}2.png")
    val secondSidecarFile = File("${prefix}2.uitree.json")
    val secondAnnotatedFile = File("${prefix}2.annotated.png")
    val allFiles = listOf(
      imageFile, sidecarFile, annotatedFile,
      secondImageFile, secondSidecarFile, secondAnnotatedFile,
    )
    allFiles.forEach { it.delete() }

    onView(isRoot()).captureRoboImage(
      file = imageFile,
      roborazziOptions = RoborazziOptions(
        taskType = RoborazziTaskType.Record,
        uiTreeDumpOptions = UiTreeDumpOptions(),
      ),
    )

    val json = sidecarFile.readText()

    // The action labels are preserved with a stable, label-only, sorted
    // rendering, so the sidecar stays byte-identical across runs.
    assertTrue(
      "expected label-only CustomActions rendering in:\n$json",
      json.contains(
        "\"CustomActions\": \"[CustomAccessibilityAction(label=More actions), " +
          "CustomAccessibilityAction(label=Second action)]\""
      )
    )

    // No JVM runtime identity (lambda class name / identityHashCode) may leak
    // into the JSON — that would make re-recording the same UI produce a diff.
    val identityLeak = Regex("@[0-9a-fA-F]{4,}|Lambda|Function0").find(json)
    assertTrue(
      "runtime identity leaked into the sidecar (${identityLeak?.value}):\n$json",
      identityLeak == null
    )

    // Recording the same UI again yields a byte-identical sidecar.
    onView(isRoot()).captureRoboImage(
      file = secondImageFile,
      roborazziOptions = RoborazziOptions(
        taskType = RoborazziTaskType.Record,
        uiTreeDumpOptions = UiTreeDumpOptions(),
      ),
    )
    assertEquals(json, secondSidecarFile.readText())

    allFiles.forEach { it.delete() }
  }

  /**
   * Reproduces the general case behind #923: any semantics value whose type doesn't override
   * `toString()` falls back to the JVM's default `<ClassName>@<hex identity hash>` rendering --
   * the same bug #911 fixed, but for [CustomAccessibilityAction] specifically. A concrete
   * real-world instance is the `Shape` androidx.compose.foundation sets internally on some
   * scrollable containers (e.g. `VerticalScrollableClipShape`), which has no custom `toString()`;
   * this test reproduces the underlying defect directly via a plain custom semantics value so it
   * doesn't depend on which Compose foundation version does or doesn't set that `Shape`. A fresh
   * instance is created on every composition, so its identity hash would differ between the two
   * captures below unless it's stripped.
   *
   * Also covers the inverse: a value with a genuinely custom `toString()` that merely resembles
   * the default format ([ValueWithCustomToStringResemblingDefault]) must be left untouched --
   * `toStableString()` has to check that a rendering *is* the default for that specific instance,
   * not just that it looks like it could be.
   */
  @Test
  fun unstableDefaultToStringValuesAreSerializedWithoutIdentityHash() {
    composeTestRule.setContent {
      Text(
        text = "Item",
        modifier = Modifier
          .testTag("item")
          .semantics {
            this[UnstableToStringTestKey] = ValueWithUnstableDefaultToString()
            this[CustomToStringResemblingDefaultTestKey] = ValueWithCustomToStringResemblingDefault()
          }
      )
    }

    val prefix =
      "${roborazziSystemPropertyOutputDirectory()}/${this::class.qualifiedName}.unstableToString"
    val imageFile = File("$prefix.png")
    val sidecarFile = File("$prefix.uitree.json")
    val annotatedFile = File("$prefix.annotated.png")
    val secondImageFile = File("${prefix}2.png")
    val secondSidecarFile = File("${prefix}2.uitree.json")
    val secondAnnotatedFile = File("${prefix}2.annotated.png")
    val allFiles = listOf(
      imageFile, sidecarFile, annotatedFile,
      secondImageFile, secondSidecarFile, secondAnnotatedFile,
    )
    allFiles.forEach { it.delete() }

    onView(isRoot()).captureRoboImage(
      file = imageFile,
      roborazziOptions = RoborazziOptions(
        taskType = RoborazziTaskType.Record,
        uiTreeDumpOptions = UiTreeDumpOptions(),
      ),
    )

    val json = sidecarFile.readText()

    // The identity hash is stripped, leaving just the stable class name.
    assertTrue(
      "expected identity-hash-free class name in:\n$json",
      json.contains(
        "\"UnstableToStringTestValue\": " +
          "\"com.github.takahirom.roborazzi.sample.ValueWithUnstableDefaultToString\""
      )
    )

    // A custom toString() that merely resembles the default format (own class name + "@" +
    // something hex-looking) is left untouched -- only the actual default rendering is stripped.
    assertTrue(
      "expected untouched custom toString() output in:\n$json",
      json.contains("\"CustomToStringResemblingDefaultTestValue\": \"$CUSTOM_TO_STRING_RESEMBLING_DEFAULT\"")
    )

    // No JVM runtime identity (default Object#toString() identity hash / lambda class name)
    // may leak into the JSON -- that would make re-recording the same UI produce a diff. Excludes
    // the deliberately-preserved custom toString() above, which matches this shape on purpose.
    val identityLeak = Regex("@[0-9a-fA-F]{4,}|Lambda|Function0")
      .find(json.replace(CUSTOM_TO_STRING_RESEMBLING_DEFAULT, ""))
    assertTrue(
      "runtime identity leaked into the sidecar (${identityLeak?.value}):\n$json",
      identityLeak == null
    )

    // Recording the same UI again yields a byte-identical sidecar, even though composition
    // created a brand new (differently-hashed) value instance for this second capture.
    onView(isRoot()).captureRoboImage(
      file = secondImageFile,
      roborazziOptions = RoborazziOptions(
        taskType = RoborazziTaskType.Record,
        uiTreeDumpOptions = UiTreeDumpOptions(),
      ),
    )
    assertEquals(json, secondSidecarFile.readText())

    allFiles.forEach { it.delete() }
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
    var hasDifferingPixels = false
    outer@ for (x in 0 until screenshot.width) {
      for (y in 0 until screenshot.height) {
        if (screenshot.getPixel(x, y) != annotated.getPixel(x, y)) {
          hasDifferingPixels = true
          break@outer
        }
      }
    }
    assertTrue("annotated image is identical to the screenshot", hasDifferingPixels)

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

private val UnstableToStringTestKey = SemanticsPropertyKey<Any>("UnstableToStringTestValue")

/**
 * Deliberately has no `toString()` override, so it falls back to the JVM's default
 * `<ClassName>@<hex identity hash>` rendering -- the case `semanticsValueToString()` must
 * sanitize.
 */
private class ValueWithUnstableDefaultToString

private const val CUSTOM_TO_STRING_RESEMBLING_DEFAULT = "com.example.Token@f00d"

private val CustomToStringResemblingDefaultTestKey =
  SemanticsPropertyKey<Any>("CustomToStringResemblingDefaultTestValue")

/**
 * Overrides `toString()` with output that merely resembles the JVM's default
 * `Object#toString()` format (its own class name followed by "@" and something hex-looking)
 * without actually being it -- `toStableString()` must recognize this is *not* the unstable
 * default rendering for this instance and leave it untouched.
 */
private class ValueWithCustomToStringResemblingDefault {
  override fun toString() = CUSTOM_TO_STRING_RESEMBLING_DEFAULT
}
