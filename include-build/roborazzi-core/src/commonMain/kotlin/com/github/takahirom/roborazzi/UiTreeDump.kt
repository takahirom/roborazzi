package com.github.takahirom.roborazzi

/**
 * The context data key under which the written `.uitree.json` sidecar path is
 * recorded on a capture result so reports can link to it.
 */
@ExperimentalRoborazziApi
const val ROBORAZZI_UI_TREE_FILE_PATH_KEY: String = "roborazzi_uitree_file_path"

/**
 * The default file-name suffix of the written UI tree JSON sidecar (for example
 * `MyTest.uitree.json`, `MyTest_actual.uitree.json`). Used by the capture wiring
 * to name the file and by the Gradle plugin to recognize stale sidecars.
 */
@InternalRoborazziApi
const val roborazziUiTreeSidecarSuffix: String = ".uitree.json"

/**
 * Default value for [RoborazziOptions.uiTreeDumpOptions]. Returns an enabled
 * [UiTreeDumpOptions] when the Gradle/system property `roborazzi.dumpUiTree` is
 * `true`, otherwise null (disabled).
 */
@ExperimentalRoborazziApi
fun defaultUiTreeDumpOptions(): UiTreeDumpOptions? =
  if (getSystemProperty("roborazzi.dumpUiTree", "false").toBoolean()) {
    UiTreeDumpOptions()
  } else {
    null
  }

/**
 * Options controlling the "UI tree dump" JSON sidecar.
 *
 * When present on [RoborazziOptions.uiTreeDumpOptions], every captured
 * screenshot gets a machine-readable `.uitree.json` file written next to the
 * image describing the Compose semantics + View hierarchy of the current run.
 *
 * The sidecar is informational only: it never participates in image diffing and
 * never fails verification.
 */
@ExperimentalRoborazziApi
class UiTreeDumpOptions(
  /**
   * Predicate deciding whether a node is "annotatable" and therefore receives a
   * sequential `n` number in the JSON (and, in a later step, a drawn marker).
   *
   * The default marks a node annotatable when it is visible AND it either has a
   * test tag, exposes a Text or ContentDescription property, or has at least one
   * action. Numbering itself is applied in pre-order; once an annotatable node
   * that has the "MergeDescendants" flag receives a number, its descendants are
   * skipped (see [assignUiTreeNumbers]).
   */
  val isAnnotatable: (RoboComponentTree) -> Boolean = DefaultIsAnnotatable,
) {
  companion object {
    @ExperimentalRoborazziApi
    val DefaultIsAnnotatable: (RoboComponentTree) -> Boolean = { node ->
      node.visibility == Visibility.Visible &&
        (node.testTag != null ||
          node.properties.containsKey("Text") ||
          node.properties.containsKey("ContentDescription") ||
          node.actions.isNotEmpty())
    }
  }
}

/**
 * Internal tooling type carrying the OUTPUT image geometry recorded at the root
 * of the UI tree JSON so consumers can map RAW (unscaled) node
 * [RoboComponentTree.bounds] onto the image that the task actually wrote.
 */
@InternalRoborazziApi
data class UiTreeCaptureInfo(
  val imageWidth: Int,
  val imageHeight: Int,
  val scale: Double,
)

private const val MergeDescendantsFlag = "MergeDescendants"

/**
 * Assigns sequential (1-based, pre-order) numbers to the annotatable nodes of
 * [root]. Nodes that are not annotatable get no number. Once an annotatable node
 * carrying the "MergeDescendants" flag receives a number, none of its
 * descendants are numbered.
 *
 * Numbers are keyed by node instance. Node implementations (Roborazzi's
 * `RoboComponent` subclasses) use reference identity, so equal-but-distinct
 * nodes are handled correctly; do not implement structural equality on tree
 * node types you want numbered independently.
 *
 * Internal tooling helper backing the sidecar/annotation pipeline.
 */
@InternalRoborazziApi
fun assignUiTreeNumbers(
  root: RoboComponentTree,
  isAnnotatable: (RoboComponentTree) -> Boolean,
): Map<RoboComponentTree, Int> {
  val result = LinkedHashMap<RoboComponentTree, Int>()
  var counter = 0
  fun visit(node: RoboComponentTree) {
    val numbered = isAnnotatable(node)
    if (numbered) {
      counter += 1
      result[node] = counter
      if (node.flags.contains(MergeDescendantsFlag)) {
        return
      }
    }
    node.children.forEach { visit(it) }
  }
  visit(root)
  return result
}

/**
 * Serializes [this] tree into the grep-first UI tree JSON described in the
 * Roborazzi docs. Deterministic: the same tree yields byte-identical output.
 *
 * Internal tooling helper backing the sidecar/annotation pipeline; the
 * user-facing surface is [UiTreeDumpOptions] and the written sidecar files.
 */
@InternalRoborazziApi
fun RoboComponentTree.toUiTreeJson(
  captureInfo: UiTreeCaptureInfo,
  options: UiTreeDumpOptions = UiTreeDumpOptions(),
): String {
  val numbers = assignUiTreeNumbers(this, options.isAnnotatable)
  val builder = StringBuilder()
  builder.append("{ \"schemaVersion\": 1, \"capture\": { ")
  builder.append("\"imageWidth\": ").append(captureInfo.imageWidth)
  builder.append(", \"imageHeight\": ").append(captureInfo.imageHeight)
  builder.append(", \"scale\": ").append(formatScale(captureInfo.scale))
  builder.append(" }, \"root\":\n")
  builder.append(serializeNode(this, depth = 1, numbers = numbers))
  builder.append("\n}")
  return builder.toString()
}

private fun serializeNode(
  node: RoboComponentTree,
  depth: Int,
  numbers: Map<RoboComponentTree, Int>,
): String {
  val indent = " ".repeat(depth)
  val scalars = buildScalars(node, numbers)
  if (node.children.isEmpty()) {
    return "$indent{ $scalars }"
  }
  val childBlocks = node.children.joinToString(",\n") {
    serializeNode(it, depth + 1, numbers)
  }
  // Fold the closing "] }" onto the last child's line (never a brackets-only line).
  return "$indent{ $scalars, \"children\": [\n$childBlocks ] }"
}

private fun buildScalars(
  node: RoboComponentTree,
  numbers: Map<RoboComponentTree, Int>,
): String {
  val parts = mutableListOf<String>()
  numbers[node]?.let { parts.add("\"n\": $it") }
  parts.add("\"type\": \"${node.treeType.jsonValue}\"")
  node.className?.let { parts.add("\"className\": \"${jsonEscape(it)}\"") }
  node.resourceId?.let { parts.add("\"id\": \"${jsonEscape(it)}\"") }
  node.testTag?.let { parts.add("\"testTag\": \"${jsonEscape(it)}\"") }
  val b = node.bounds
  parts.add("\"bounds\": [${b.left}, ${b.top}, ${b.right}, ${b.bottom}]")
  when (node.visibility) {
    Visibility.Visible -> {} // Omit when visible.
    Visibility.Invisible -> parts.add("\"visibility\": \"invisible\"")
    Visibility.Gone -> parts.add("\"visibility\": \"gone\"")
  }
  if (node.properties.isNotEmpty()) {
    // Sort keys so the output is deterministic regardless of the input map's
    // iteration order (Roborazzi's extraction already produces sorted keys).
    val entries = node.properties.entries
      .sortedBy { it.key }
      .joinToString(", ") { (key, value) ->
        "\"${jsonEscape(key)}\": \"${jsonEscape(value)}\""
      }
    parts.add("\"properties\": { $entries }")
  }
  if (node.actions.isNotEmpty()) {
    val entries = node.actions.joinToString(", ") { "\"${jsonEscape(it)}\"" }
    parts.add("\"actions\": [$entries]")
  }
  if (node.flags.isNotEmpty()) {
    val entries = node.flags.joinToString(", ") { "\"${jsonEscape(it)}\"" }
    parts.add("\"flags\": [$entries]")
  }
  return parts.joinToString(", ")
}

/**
 * Formats the resize scale deterministically. Whole numbers keep one decimal
 * (e.g. 1.0) to stay valid, un-ambiguous JSON.
 */
private fun formatScale(scale: Double): String {
  val asString = scale.toString()
  return if (asString.contains('.') || asString.contains('e') || asString.contains('E')) {
    asString
  } else {
    "$asString.0"
  }
}

private fun jsonEscape(value: String): String {
  val sb = StringBuilder(value.length + 2)
  for (ch in value) {
    when (ch) {
      '"' -> sb.append("\\\"")
      '\\' -> sb.append("\\\\")
      '\n' -> sb.append("\\n")
      '\r' -> sb.append("\\r")
      '\t' -> sb.append("\\t")
      '\b' -> sb.append("\\b")
      '\u000C' -> sb.append("\\f")
      else -> if (ch < ' ') {
        sb.append("\\u")
        val hex = ch.code.toString(16)
        sb.append("0000".substring(hex.length))
        sb.append(hex)
      } else {
        sb.append(ch)
      }
    }
  }
  return sb.toString()
}
