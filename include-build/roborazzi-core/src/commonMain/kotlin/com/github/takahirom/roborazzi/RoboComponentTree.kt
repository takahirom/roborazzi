package com.github.takahirom.roborazzi

/**
 * Visibility of a component in the UI tree.
 *
 * Moved from the Android-only capture code so that the platform-independent
 * [RoboComponentTree] can expose it on every target.
 */
enum class Visibility {
  Visible, Gone, Invisible;
}

/**
 * Pure-Kotlin geometry type describing the bounds of a component.
 *
 * This mirrors the meaningful part of `android.graphics.Rect` (integer edges)
 * without depending on any platform, so the tree structure can be shared across
 * Android, JVM and iOS targets.
 */
data class RoboRect(
  val left: Int,
  val top: Int,
  val right: Int,
  val bottom: Int,
) {
  val width: Int get() = right - left
  val height: Int get() = bottom - top
}

/**
 * Discriminates the kind of node in a [RoboComponentTree].
 *
 * Serialized into the UI tree JSON as the lowercase `type` value
 * ("screen", "view", "compose").
 */
enum class RoboComponentTreeType {
  Screen, View, Compose;

  /** The value emitted for the JSON `type` key. */
  val jsonValue: String get() = name.lowercase()
}

/**
 * Platform-independent structure of a captured UI component.
 *
 * This carries the tree shape and the structured accessibility/semantics data
 * that can be represented on every platform. Platform-specific surfaces (for
 * example Android's `RoboComponent` with `image`, `rect`, `text`) refine this
 * interface by adding their own members.
 */
interface RoboComponentTree {
  val children: List<RoboComponentTree>
  val bounds: RoboRect
  val visibility: Visibility
  val width: Int
  val height: Int

  /** The kind of node, used as the `type` discriminator in the UI tree JSON. */
  val treeType: RoboComponentTreeType

  /**
   * Fully-qualified class name for platform view nodes, or null when not
   * applicable (for example Compose or screen nodes).
   */
  val className: String? get() = null

  /**
   * Resource id name of a platform view (for example
   * "com.example:id/foo"), or null when the node has no id.
   */
  val resourceId: String? get() = null

  /**
   * Structured accessibility/semantics properties keyed by property name.
   *
   * Only meaningful entries are present; there are no always-on placeholder
   * entries. For Compose nodes this mirrors the non-action, non-flag entries
   * printed by the semantics dump. For platform views this holds structured
   * entries such as Text, ContentDescription and StateDescription, each only
   * when present. Notable boolean states are reported through [flags] instead.
   */
  val properties: Map<String, String>

  /** Names of accessibility/semantics actions available on this component. */
  val actions: List<String>

  /**
   * Names of boolean/flag-like semantics that are active on this component,
   * sorted for determinism. A flag is only present when it is on (for example
   * "Clickable", "Focused" or "Disabled"), never as an explicit "false" entry.
   */
  val flags: List<String>

  /** The Compose test tag, or null for components that do not have one. */
  val testTag: String?

  fun depth(): Int {
    return (children.maxOfOrNull {
      it.depth()
    } ?: 0) + 1
  }

  fun countOfComponent(): Int {
    return children.sumOf {
      it.countOfComponent()
    } + 1
  }
}
