package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoboComponentTree
import com.github.takahirom.roborazzi.RoboComponentTreeType
import com.github.takahirom.roborazzi.RoboRect
import com.github.takahirom.roborazzi.UiTreeCaptureInfo
import com.github.takahirom.roborazzi.UiTreeDumpOptions
import com.github.takahirom.roborazzi.Visibility
import com.github.takahirom.roborazzi.assignUiTreeNumbers
import com.github.takahirom.roborazzi.toUiTreeJson
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A regular (non-data) [RoboComponentTree] so nodes are distinguished by
 * reference identity, matching Roborazzi's real `RoboComponent` subclasses.
 */
private class FakeNode(
  override val treeType: RoboComponentTreeType,
  override val bounds: RoboRect,
  override val visibility: Visibility = Visibility.Visible,
  override val testTag: String? = null,
  override val className: String? = null,
  override val resourceId: String? = null,
  override val properties: Map<String, String> = emptyMap(),
  override val actions: List<String> = emptyList(),
  override val flags: List<String> = emptyList(),
  override val children: List<RoboComponentTree> = emptyList(),
) : RoboComponentTree {
  override val width: Int get() = bounds.width
  override val height: Int get() = bounds.height
}

@OptIn(ExperimentalRoborazziApi::class)
class UiTreeDumpTest {

  private fun sampleTree(): RoboComponentTree = FakeNode(
    treeType = RoboComponentTreeType.View,
    className = "androidx.compose.ui.platform.ComposeView",
    bounds = RoboRect(0, 0, 220, 100),
    children = listOf(
      FakeNode(
        treeType = RoboComponentTreeType.Compose,
        testTag = "login_button",
        bounds = RoboRect(16, 24, 204, 72),
        properties = linkedMapOf("Role" to "Button", "Text" to "Login"),
        actions = listOf("OnClick"),
        flags = listOf("MergeDescendants"),
      ),
      FakeNode(
        treeType = RoboComponentTreeType.Compose,
        bounds = RoboRect(16, 80, 204, 96),
        properties = mapOf("Text" to "Forgot password?"),
      ),
    ),
  )

  private val captureInfo = UiTreeCaptureInfo(imageWidth = 220, imageHeight = 100, scale = 1.0)

  @Test
  fun exactStringMatchesSpec() {
    val expected = """
{ "schemaVersion": 1, "capture": { "imageWidth": 220, "imageHeight": 100, "scale": 1.0 }, "root":
 { "type": "view", "className": "androidx.compose.ui.platform.ComposeView", "bounds": [0, 0, 220, 100], "children": [
  { "n": 1, "type": "compose", "testTag": "login_button", "bounds": [16, 24, 204, 72], "properties": { "Role": "Button", "Text": "Login" }, "actions": ["OnClick"], "flags": ["MergeDescendants"] },
  { "n": 2, "type": "compose", "bounds": [16, 80, 204, 96], "properties": { "Text": "Forgot password?" } } ] }
}
""".trim()
    assertEquals(expected, sampleTree().toUiTreeJson(captureInfo))
  }

  @Test
  fun outputIsValidJson() {
    val json = sampleTree().toUiTreeJson(captureInfo)
    // Throws if invalid.
    Json.parseToJsonElement(json)
  }

  @Test
  fun deterministicAcrossRuns() {
    assertEquals(
      sampleTree().toUiTreeJson(captureInfo),
      sampleTree().toUiTreeJson(captureInfo),
    )
  }

  @Test
  fun onePropertyPerNodeOnOneLine() {
    val json = sampleTree().toUiTreeJson(captureInfo)
    // The login_button node's testTag and bounds must be on the same single line.
    val matching = json.lines().filter { it.contains("\"login_button\"") }
    assertEquals(1, matching.size)
    assertTrue(matching.single().contains("\"bounds\": [16, 24, 204, 72]"))
    // Brackets fold onto content lines: every line except the final top-level
    // "}" carries node content (a quoted key), so no line is brackets-only.
    val lines = json.lines()
    lines.dropLast(1).forEach { assertTrue("brackets-only line: '$it'", it.contains('"')) }
    assertEquals("}", lines.last())
  }

  @Test
  fun mergeDescendantsSuppressesDescendantNumbers() {
    val child = FakeNode(
      treeType = RoboComponentTreeType.Compose,
      testTag = "inner",
      bounds = RoboRect(0, 0, 10, 10),
    )
    val merged = FakeNode(
      treeType = RoboComponentTreeType.Compose,
      testTag = "merged",
      bounds = RoboRect(0, 0, 100, 100),
      flags = listOf("MergeDescendants"),
      children = listOf(child),
    )
    val root = FakeNode(
      treeType = RoboComponentTreeType.View,
      bounds = RoboRect(0, 0, 100, 100),
      children = listOf(merged),
    )
    val numbers = assignUiTreeNumbers(root, UiTreeDumpOptions.DefaultIsAnnotatable)
    assertEquals(1, numbers[merged])
    // The descendant of a numbered MergeDescendants node gets no number.
    assertNull(numbers[child])
  }

  @Test
  fun nonAnnotatableNodesGetNoNumber() {
    // Invisible node with a testTag is not annotatable.
    val invisible = FakeNode(
      treeType = RoboComponentTreeType.Compose,
      testTag = "hidden",
      visibility = Visibility.Invisible,
      bounds = RoboRect(0, 0, 10, 10),
    )
    // Visible plain node with no tag/text/actions is not annotatable.
    val plain = FakeNode(
      treeType = RoboComponentTreeType.Compose,
      bounds = RoboRect(0, 0, 10, 10),
    )
    val annotatable = FakeNode(
      treeType = RoboComponentTreeType.Compose,
      testTag = "visible_tag",
      bounds = RoboRect(0, 0, 10, 10),
    )
    val root = FakeNode(
      treeType = RoboComponentTreeType.View,
      bounds = RoboRect(0, 0, 100, 100),
      children = listOf(invisible, plain, annotatable),
    )
    val numbers = assignUiTreeNumbers(root, UiTreeDumpOptions.DefaultIsAnnotatable)
    assertNull(numbers[invisible])
    assertNull(numbers[plain])
    assertEquals(1, numbers[annotatable])
  }

  @Test
  fun omitsEmptyFieldsAndVisibleVisibility() {
    val node = FakeNode(
      treeType = RoboComponentTreeType.Compose,
      bounds = RoboRect(1, 2, 3, 4),
    )
    val json = node.toUiTreeJson(captureInfo)
    assertTrue(json.contains("\"type\": \"compose\", \"bounds\": [1, 2, 3, 4]"))
    // No empty maps/lists or visibility=visible are emitted.
    assertTrue(!json.contains("\"properties\""))
    assertTrue(!json.contains("\"actions\""))
    assertTrue(!json.contains("\"flags\""))
    assertTrue(!json.contains("\"visibility\""))
    assertTrue(!json.contains("\"testTag\""))
    assertTrue(!json.contains("\"className\""))
    assertTrue(!json.contains("\"id\""))
    assertTrue(!json.contains("\"n\""))
  }

  @Test
  fun emitsNonVisibleVisibility() {
    val gone = FakeNode(
      treeType = RoboComponentTreeType.View,
      bounds = RoboRect(0, 0, 1, 1),
      visibility = Visibility.Gone,
    )
    assertTrue(gone.toUiTreeJson(captureInfo).contains("\"visibility\": \"gone\""))
    val invisible = FakeNode(
      treeType = RoboComponentTreeType.View,
      bounds = RoboRect(0, 0, 1, 1),
      visibility = Visibility.Invisible,
    )
    assertTrue(invisible.toUiTreeJson(captureInfo).contains("\"visibility\": \"invisible\""))
  }

  @Test
  fun emitsViewClassNameAndId() {
    val node = FakeNode(
      treeType = RoboComponentTreeType.View,
      bounds = RoboRect(0, 0, 1, 1),
      className = "android.widget.TextView",
      resourceId = "com.example:id/foo",
    )
    val json = node.toUiTreeJson(captureInfo)
    assertTrue(json.contains("\"className\": \"android.widget.TextView\""))
    assertTrue(json.contains("\"id\": \"com.example:id/foo\""))
  }

  @Test
  fun escapesSpecialCharactersInStrings() {
    val node = FakeNode(
      treeType = RoboComponentTreeType.Compose,
      bounds = RoboRect(0, 0, 1, 1),
      properties = mapOf("Text" to "line1\nquote\"end"),
    )
    val json = node.toUiTreeJson(captureInfo)
    assertTrue(json.contains("""line1\nquote\"end"""))
    // Still valid JSON after escaping.
    Json.parseToJsonElement(json)
  }
}
