package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.RoboComponentTree
import com.github.takahirom.roborazzi.RoboComponentTreeType
import com.github.takahirom.roborazzi.RoboRect
import com.github.takahirom.roborazzi.UiTreeCaptureInfo
import com.github.takahirom.roborazzi.UiTreeDumpOptions
import com.github.takahirom.roborazzi.Visibility
import com.github.takahirom.roborazzi.assignUiTreeNumbers
import com.github.takahirom.roborazzi.computeUiTreeAnnotations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A reference-identity [RoboComponentTree] node for the annotation tests. */
private class AnnNode(
  override val bounds: RoboRect,
  override val visibility: Visibility = Visibility.Visible,
  override val testTag: String? = null,
  override val actions: List<String> = emptyList(),
  override val flags: List<String> = emptyList(),
  override val children: List<RoboComponentTree> = emptyList(),
  override val treeType: RoboComponentTreeType = RoboComponentTreeType.Compose,
  override val properties: Map<String, String> = emptyMap(),
) : RoboComponentTree {
  override val width: Int get() = bounds.width
  override val height: Int get() = bounds.height
}

@OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
class UiTreeAnnotationTest {

  private fun annotate(
    root: RoboComponentTree,
    captureInfo: UiTreeCaptureInfo,
  ) = computeUiTreeAnnotations(
    root = root,
    numbers = assignUiTreeNumbers(root, UiTreeDumpOptions.DefaultIsAnnotatable),
    captureInfo = captureInfo,
  )

  @Test
  fun mapsRawWindowBoundsWithRootOffsetAndScale() {
    // Root starts at a non-zero window origin (single-component capture case).
    val child = AnnNode(
      bounds = RoboRect(16, 380, 78, 420),
      testTag = "button",
    )
    val root = AnnNode(
      bounds = RoboRect(0, 358, 94, 526),
      children = listOf(child),
    )
    // scale 1.0 -> image = raw - root.origin.
    val annotations = annotate(root, UiTreeCaptureInfo(94, 168, 1.0))
    assertEquals(1, annotations.size)
    val ann = annotations.single()
    assertEquals(1, ann.number)
    // (16-0, 380-358, 78-0, 420-358)
    assertEquals(RoboRect(16, 22, 78, 62), ann.bounds)
  }

  @Test
  fun appliesScale() {
    val child = AnnNode(bounds = RoboRect(10, 20, 30, 40), testTag = "x")
    val root = AnnNode(bounds = RoboRect(0, 0, 100, 100), children = listOf(child))
    val annotations = annotate(root, UiTreeCaptureInfo(200, 200, 2.0))
    assertEquals(RoboRect(20, 40, 60, 80), annotations.single().bounds)
  }

  @Test
  fun clampsBoxesToImageDimensions() {
    val child = AnnNode(bounds = RoboRect(-10, -10, 120, 130), testTag = "big")
    val root = AnnNode(bounds = RoboRect(0, 0, 100, 100), children = listOf(child))
    val annotations = annotate(root, UiTreeCaptureInfo(100, 100, 1.0))
    // Left/top clamp to 0, right/bottom clamp to image size.
    assertEquals(RoboRect(0, 0, 100, 100), annotations.single().bounds)
  }

  @Test
  fun skipsNodesFullyOutsideImage() {
    val below = AnnNode(bounds = RoboRect(0, 200, 50, 250), testTag = "below")
    val right = AnnNode(bounds = RoboRect(200, 0, 250, 50), testTag = "right")
    val root = AnnNode(bounds = RoboRect(0, 0, 100, 100), children = listOf(below, right))
    val annotations = annotate(root, UiTreeCaptureInfo(100, 100, 1.0))
    assertTrue("expected no annotations, got $annotations", annotations.isEmpty())
  }

  @Test
  fun skipsDegenerateBoxes() {
    // Zero-width box (left == right).
    val zeroWidth = AnnNode(bounds = RoboRect(50, 10, 50, 40), testTag = "zw")
    // Box entirely at the left edge clamps to width 0.
    val atLeftEdge = AnnNode(bounds = RoboRect(-20, 10, 0, 40), testTag = "edge")
    val root = AnnNode(
      bounds = RoboRect(0, 0, 100, 100),
      children = listOf(zeroWidth, atLeftEdge),
    )
    val annotations = annotate(root, UiTreeCaptureInfo(100, 100, 1.0))
    assertTrue("expected no annotations, got $annotations", annotations.isEmpty())
  }

  @Test
  fun numbersMatchAssignedNumbersInOrder() {
    val first = AnnNode(bounds = RoboRect(0, 0, 10, 10), testTag = "first")
    val second = AnnNode(bounds = RoboRect(0, 20, 10, 30), testTag = "second")
    val root = AnnNode(bounds = RoboRect(0, 0, 100, 100), children = listOf(first, second))
    val annotations = annotate(root, UiTreeCaptureInfo(100, 100, 1.0))
    assertEquals(listOf(1, 2), annotations.map { it.number })
  }
}
