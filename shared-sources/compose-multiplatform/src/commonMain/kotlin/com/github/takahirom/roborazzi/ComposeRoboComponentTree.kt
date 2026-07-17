package com.github.takahirom.roborazzi

import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import kotlin.math.roundToInt

/**
 * Platform-independent [RoboComponentTree] built from a Compose [SemanticsNode].
 *
 * This is the desktop (JVM) / iOS counterpart of Android's
 * `RoboComponent.Compose`. It uses only multiplatform compose-ui APIs, so the
 * exact same source file is compiled into both the Compose Desktop and Compose
 * iOS modules (shared via a `srcDir`). Keeping a single source copy avoids
 * duplicating the semantics extraction logic per platform.
 *
 * Bounds contract: node [bounds] are the RAW, unscaled window coordinates
 * (`SemanticsNode.boundsInWindow`, rounded to integer edges), matching Android's
 * `boundsInWindow` mapping. The root node's origin maps to the output image
 * origin; the resize scale recorded in the UI tree JSON maps raw bounds onto the
 * scaled output image.
 */
internal class ComposeRoboComponentTree(
  node: SemanticsNode,
) : RoboComponentTree {
  override val width: Int = node.layoutInfo.width
  override val height: Int = node.layoutInfo.height

  override val children: List<RoboComponentTree> =
    node.children.map { ComposeRoboComponentTree(it) }

  override val bounds: RoboRect = run {
    val rect = node.boundsInWindow
    RoboRect(
      left = rect.left.roundToInt(),
      top = rect.top.roundToInt(),
      right = rect.right.roundToInt(),
      bottom = rect.bottom.roundToInt(),
    )
  }

  // Compose nodes are considered visible; the semantics tree does not expose a
  // Gone/Invisible distinction the way the Android View hierarchy does. This
  // matches Android's `RoboComponent.Compose.visibility`.
  override val visibility: Visibility = Visibility.Visible

  override val treeType: RoboComponentTreeType = RoboComponentTreeType.Compose

  override val testTag: String? = node.config.getOrNull(SemanticsProperties.TestTag)

  override val properties: Map<String, String> = node.config.toRoboProperties()
  override val actions: List<String> = node.config.toRoboActions()
  override val flags: List<String> = node.config.toRoboFlags()
}

/**
 * Builds a [ComposeRoboComponentTree] for [this] node.
 */
internal fun SemanticsNode.toRoboComponentTree(): RoboComponentTree =
  ComposeRoboComponentTree(this)

/**
 * Stringifies a semantics value exactly the way Android's semantics dump does, so
 * the structured [RoboComponentTree.properties] map stays consistent across
 * platforms.
 */
private fun semanticsValueToString(value: Any?): String = buildString {
  if (value is AnnotatedString) {
    if (value.paragraphStyles.isEmpty() && value.spanStyles.isEmpty() && value
        .getStringAnnotations(0, value.text.length).isEmpty()
    ) {
      append(value.text)
    } else {
      append(value)
    }
  } else if (value is Iterable<*>) {
    append(value.sortedBy { it.toString() })
  } else if (value is CollectionInfo) {
    append("(rowCount=${value.rowCount}, columnCount=${value.columnCount})")
  } else {
    append(value)
  }
}

/**
 * Extracts the non-action, non-flag semantics into a name -> value map, using the
 * same filtering and stringification rules as Android's semantics dump.
 */
internal fun SemanticsConfiguration.toRoboProperties(): Map<String, String> {
  val result = LinkedHashMap<String, String>()
  for ((key, value) in this.sortedBy { it.key.name }) {
    if (key == SemanticsProperties.TestTag) continue
    if (value is AccessibilityAction<*> || value is Function<*>) continue
    if (value is Unit) continue
    result[key.name] = semanticsValueToString(value)
  }
  return result
}

/** Extracts the names of action-valued semantics keys, sorted for determinism. */
internal fun SemanticsConfiguration.toRoboActions(): List<String> {
  val actions = mutableListOf<String>()
  for ((key, value) in this) {
    if (key == SemanticsProperties.TestTag) continue
    if (value is AccessibilityAction<*> || value is Function<*>) {
      actions.add(key.name)
    }
  }
  return actions.sorted()
}

/**
 * Extracts flag-like semantics: Unit-valued keys plus MergeDescendants and
 * ClearAndSetSemantics, sorted for determinism.
 */
internal fun SemanticsConfiguration.toRoboFlags(): List<String> {
  val flags = mutableListOf<String>()
  for ((key, value) in this) {
    if (key == SemanticsProperties.TestTag) continue
    if (value is Unit) {
      flags.add(key.name)
    }
  }
  if (isMergingSemanticsOfDescendants) {
    flags.add("MergeDescendants")
  }
  if (isClearingSemantics) {
    flags.add("ClearAndSetSemantics")
  }
  return flags.sorted()
}
