package com.github.takahirom.roborazzi

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import android.widget.TextView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import org.robolectric.Shadows
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One element of the accessibility legend.
 *
 * [order] is the position in the screen reader traversal order, null for an element outside of
 * it. [container] is set for a pane, which is outlined instead of numbered.
 */
internal data class AccessibilityLegendEntry(
  val line: String,
  val details: List<String>,
  val bounds: Rect,
  val problem: Boolean = false,
  val order: Double? = null,
  val offscreen: Boolean = false,
  val container: String? = null,
)

/** The legend of [rootComponent] as text, one numbered element per line. */
@InternalRoborazziApi
fun accessibilityLegendText(rootComponent: RoboComponent): String {
  val entries = collectAccessibilityLegendEntries(rootComponent)
  val elements = entries.filter { it.container == null }.mapIndexed { index, entry ->
    (listOf("${index + 1}. ${entry.line}") + entry.details.map { "   $it" }).joinToString("\n")
  }
  val containers = entries.mapNotNull { it.container }.map { "[$it]" }
  return (elements + containers).joinToString("\n")
}

internal fun collectAccessibilityLegendEntries(
  rootComponent: RoboComponent,
): List<AccessibilityLegendEntry> {
  val sources = when (rootComponent) {
    is RoboComponent.View -> {
      val view = rootComponent.androidView
      listOf(LegendSource.View(view = view, origin = view.locationInWindow()))
    }

    is RoboComponent.Compose -> listOf(LegendSource.Compose(node = rootComponent.semanticsNode))
    is RoboComponent.Screen -> rootComponent.windows.map { (decorView, rect) ->
      val origin = decorView.locationInWindow().apply { offset(-rect.left, -rect.top) }
      LegendSource.View(view = decorView, origin = origin)
    }
  }
  val context = when (val source = sources.firstOrNull()) {
    is LegendSource.View -> source.view.context
    is LegendSource.Compose -> (source.node.root as? ViewRootForTest)?.view?.context
    null -> null
  } ?: return emptyList()
  val screen = Rect(0, 0, rootComponent.width, rootComponent.height)
  return withScreenReader(context = context) {
    val walk = LegendWalk(screen = screen)
    sources.forEach { source ->
      when (source) {
        is LegendSource.View -> {
          walk.origin = source.origin
          collectLabelsFor(view = source.view, walk = walk)
          visitView(view = source.view, walk = walk, insideFocusable = false)
        }

        is LegendSource.Compose -> {
          val root = source.node.root as? ViewRootForTest ?: return@forEach
          val nodeBounds = source.node.boundsInWindow
          walk.origin = Rect(nodeBounds.left.roundToInt(), nodeBounds.top.roundToInt(), 0, 0)
          visitCompose(root = root, startNode = source.node, walk = walk)
        }
      }
    }
    walk.entries.sortedWith(compareBy(nullsLast()) { it.order })
  }
}

private sealed interface LegendSource {
  class View(val view: android.view.View, val origin: Rect) : LegendSource
  class Compose(val node: SemanticsNode) : LegendSource
}

private class LegendWalk(val screen: Rect) {
  /** Window coordinates of the captured image's top-left corner. */
  var origin = Rect()
  val entries = mutableListOf<AccessibilityLegendEntry>()
  var order = 0.0
  val labelsFor = mutableMapOf<Int, String>()
}

private fun View.locationInWindow(): Rect {
  val location = IntArray(2).also(::getLocationInWindow)
  return Rect(location[0], location[1], 0, 0)
}

/**
 * Compose computes the traversal order only while a screen reader is on, so turn one on in
 * Robolectric for the duration of [block] and restore the previous state afterwards.
 */
private fun <T> withScreenReader(context: Context, block: () -> T): T {
  val manager = context.getSystemService(AccessibilityManager::class.java) ?: return block()
  val shadow = Shadows.shadowOf(manager)
  val wasEnabled = manager.isEnabled
  val wasTouchExploration = manager.isTouchExplorationEnabled
  val previousServices =
    manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
  val screenReader = AccessibilityServiceInfo().apply {
    feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN
  }
  shadow.setEnabledAccessibilityServiceList(listOf(screenReader))
  shadow.setTouchExplorationEnabled(true)
  shadow.setEnabled(true)
  Shadows.shadowOf(Looper.getMainLooper()).idle()
  try {
    return block()
  } finally {
    shadow.setEnabledAccessibilityServiceList(previousServices)
    shadow.setTouchExplorationEnabled(wasTouchExploration)
    shadow.setEnabled(wasEnabled)
    Shadows.shadowOf(Looper.getMainLooper()).idle()
  }
}

private class Description(
  val line: String,
  val details: List<String>,
  val problem: Boolean = false,
  val focusable: Boolean = true,
  val container: String? = null,
)

// region Views

private fun collectLabelsFor(view: View, walk: LegendWalk) {
  if (view.labelFor != View.NO_ID) {
    val text = (view.contentDescription ?: (view as? TextView)?.text).toLabel()
    if (text != null) walk.labelsFor[view.labelFor] = text
  }
  if (view is ViewGroup) {
    for (i in 0 until view.childCount) collectLabelsFor(view = view.getChildAt(i), walk = walk)
  }
}

private fun visitView(view: View, walk: LegendWalk, insideFocusable: Boolean) {
  if (view.visibility != View.VISIBLE) return
  if (view is ViewRootForTest) {
    visitCompose(root = view, startNode = view.semanticsOwner.rootSemanticsNode, walk = walk)
    return
  }
  var focusable = false
  if (view.isImportantForAccessibility) {
    val info = view.createAccessibilityNodeInfo()
    val description = describeView(
      view = view,
      info = info,
      walk = walk,
      insideFocusable = insideFocusable,
    )
    if (description != null) {
      val location = IntArray(2).also(view::getLocationInWindow)
      val left = location[0] - walk.origin.left
      val top = location[1] - walk.origin.top
      val rect = Rect(left, top, left + view.width, top + view.height)
      val offscreen = !info.isVisibleToUser || rect.isEmpty || !Rect.intersects(rect, walk.screen)
      walk.entries += AccessibilityLegendEntry(
        line = description.line,
        details = description.details,
        bounds = rect,
        problem = description.problem,
        order = if (description.container == null) walk.order++ else null,
        offscreen = offscreen,
        container = description.container,
      )
      focusable = description.container == null && description.focusable
    }
  }
  val children = ArrayList<View>()
  if (view is ViewGroup) view.addChildrenForAccessibility(children)
  val childInsideFocusable = insideFocusable || focusable
  children.forEach { visitView(view = it, walk = walk, insideFocusable = childInsideFocusable) }
}

private fun describeView(
  view: View,
  info: AccessibilityNodeInfo,
  walk: LegendWalk,
  insideFocusable: Boolean,
): Description? {
  val actionable = info.isClickable || info.isLongClickable || info.isCheckable ||
    (Build.VERSION.SDK_INT >= 28 && info.isScreenReaderFocusable)
  val ownLabel = (info.contentDescription ?: info.text).toLabel()
  val paneTitle = if (Build.VERSION.SDK_INT >= 28) view.accessibilityPaneTitle?.toString() else null
  val liveRegion = when (view.accessibilityLiveRegion) {
    View.ACCESSIBILITY_LIVE_REGION_POLITE -> "Polite"
    View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE -> "Assertive"
    else -> null
  }
  val speaking = ownLabel != null && !insideFocusable
  if (!actionable && !speaking && paneTitle == null && liveRegion == null) return null
  if (!actionable && ownLabel == null && paneTitle != null) return paneDescription(paneTitle)
  val label = ownLabel ?: if (actionable && view is ViewGroup) childrenText(view) else null
  val role = viewRole(info)
  val range = info.rangeInfo
  val stateDescription = (if (Build.VERSION.SDK_INT >= 30) info.stateDescription else null)
    ?.toString()?.takeUnlessSameValue(range?.current)
  val states = buildList {
    if (stateDescription == null) {
      @Suppress("DEPRECATION")
      if (info.isCheckable) add(if (info.isChecked) "checked" else "not checked")
      if (info.isSelected) add("selected")
    } else {
      add(stateDescription)
    }
    if (!info.isEnabled) add("disabled")
  }
  val details = buildList {
    if (range != null) add(rangeValue(current = range.current, min = range.min, max = range.max))
    if (info.isEditable) {
      val hint = (if (Build.VERSION.SDK_INT >= 26) info.hintText else null).toLabel()
      if (hint != null && hint != label) add("hint: \"$hint\"")
      walk.labelsFor[view.id]?.let { add("labeled by: \"$it\"") }
      info.error?.toString()?.oneLine()?.let { add("⚠ error: \"$it\"") }
      if (info.isPassword) add("password (characters hidden)")
    }
    paneTitle?.let { add("pane title: \"$it\", announced when shown") }
    liveRegion?.let { add("live region: $it, changes are announced") }
  }
  val problem = actionable && label == null
  return Description(
    line = legendLine(
      label = label,
      problem = problem,
      role = role,
      actionable = actionable,
      states = states,
    ),
    details = details,
    problem = problem,
    focusable = actionable || speaking,
  )
}

/**
 * A screen reader reads a focusable container through the texts of its non-focusable
 * descendants.
 */
private fun childrenText(group: ViewGroup): String? {
  val parts = mutableListOf<String>()
  fun visit(view: View) {
    if (view.visibility != View.VISIBLE) return
    val info = view.createAccessibilityNodeInfo()
    if (view !== group && (info.isClickable || info.isLongClickable || info.isCheckable)) return
    val text = (info.contentDescription ?: info.text).toLabel()
    if (view !== group && text != null && view.isImportantForAccessibility) {
      parts += text
      return
    }
    if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
  }
  visit(group)
  return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
}

private fun viewRole(info: AccessibilityNodeInfo): String? = when {
  Build.VERSION.SDK_INT >= 28 && info.isHeading -> "Heading"
  info.isEditable -> "EditText"
  else -> classNameRole(info.className?.toString().orEmpty())
}

private fun classNameRole(className: String): String? = when {
  className.endsWith("EditText") -> "EditText"
  className.endsWith("CheckBox") -> "Checkbox"
  className.endsWith("Switch") || className.endsWith("SwitchCompat") -> "Switch"
  className.endsWith("RadioButton") -> "RadioButton"
  className.endsWith("SeekBar") -> "Slider"
  className.endsWith("Button") -> "Button"
  className.endsWith("ImageView") -> "Image"
  else -> null
}

// endregion

// region Compose

// Compose exposes the traversal order it computes for screen readers to tests through this key.
private const val TRAVERSAL_BEFORE =
  "android.view.accessibility.extra.EXTRA_DATA_TEST_TRAVERSALBEFORE_VAL"

private const val NOT_IN_ORDER_SUFFIX = " · not in traversal order"

private val HIDDEN_KEYS = setOf("InvisibleToUser", "HideFromAccessibility")

private fun visitCompose(root: ViewRootForTest, startNode: SemanticsNode, walk: LegendWalk) {
  val provider = root.view.accessibilityNodeProvider
  val unmergedRoot = root.semanticsOwner.unmergedRootSemanticsNode
  val chain = traversalOrder(provider = provider, root = unmergedRoot)
  val local = mutableListOf<AccessibilityLegendEntry>()
  collectSemantics(
    node = startNode,
    provider = provider,
    rootId = unmergedRoot.id,
    walk = walk,
    order = chain,
    out = local,
  )
  val base = walk.order
  walk.entries += placeOutOfChain(local).map { it.copy(order = it.order?.plus(base)) }
  walk.order = base + chain.size + 1
}

/** Position of each semantics node id in the order Compose reports to screen readers. */
private fun traversalOrder(
  provider: AccessibilityNodeProvider?,
  root: SemanticsNode,
): Map<Int, Int> {
  if (provider == null || Build.VERSION.SDK_INT < 26) return emptyMap()
  val next = mutableMapOf<Int, Int>()
  fun visit(node: SemanticsNode) {
    val virtualId = if (node.id == root.id) AccessibilityNodeProvider.HOST_VIEW_ID else node.id
    provider.createAccessibilityNodeInfo(virtualId)?.let { info ->
      provider.addExtraDataToAccessibilityNodeInfo(virtualId, info, TRAVERSAL_BEFORE, null)
      if (info.extras.containsKey(TRAVERSAL_BEFORE)) {
        next[node.id] = info.extras.getInt(TRAVERSAL_BEFORE)
      }
    }
    node.children.forEach(::visit)
  }
  visit(root)
  val head = next.keys.firstOrNull { it !in next.values } ?: return emptyMap()
  val order = mutableMapOf<Int, Int>()
  var current: Int? = head
  while (current != null && current !in order) {
    order[current] = order.size
    current = next[current]
  }
  return order
}

/**
 * Compose orders only the nodes it considers screen reader focusable. A text node outside that
 * chain is still read in tree order, so place it after the chain element above it.
 */
private fun placeOutOfChain(
  entries: List<AccessibilityLegendEntry>,
): List<AccessibilityLegendEntry> {
  val chain = entries.filter { it.order != null }.sortedBy { it.order }
  return entries.map { entry ->
    val speaking = entry.line.startsWith("\"")
    val inChain = entry.order != null
    if (inChain || entry.offscreen || entry.container != null || !speaking) return@map entry
    val above = chain.lastOrNull {
      val top = it.bounds.top
      top < entry.bounds.top || (top == entry.bounds.top && it.bounds.left <= entry.bounds.left)
    }
    entry.copy(
      order = (above?.order ?: -1.0) + 0.5,
      line = entry.line.removeSuffix(NOT_IN_ORDER_SUFFIX),
    )
  }
}

private fun collectSemantics(
  node: SemanticsNode,
  provider: AccessibilityNodeProvider?,
  rootId: Int,
  walk: LegendWalk,
  order: Map<Int, Int>,
  out: MutableList<AccessibilityLegendEntry>,
) {
  val config = node.config
  if (config.any { it.key.name in HIDDEN_KEYS }) return
  // Screen readers get links as spans of the parent text, not as separate nodes.
  if (config.any { it.key.name == "LinkTestMarker" }) return
  val virtualId = if (node.id == rootId) AccessibilityNodeProvider.HOST_VIEW_ID else node.id
  val info = provider?.createAccessibilityNodeInfo(virtualId)
  describeCompose(node = node, info = info)?.let { description ->
    val bounds = node.boundsInWindow
    val rect = Rect(
      bounds.left.roundToInt() - walk.origin.left,
      bounds.top.roundToInt() - walk.origin.top,
      bounds.right.roundToInt() - walk.origin.left,
      bounds.bottom.roundToInt() - walk.origin.top,
    )
    val offscreen = rect.isEmpty || !Rect.intersects(rect, walk.screen)
    val position = order[node.id]
    val suffix = listOfNotNull(
      NOT_IN_ORDER_SUFFIX.takeIf { position == null && description.container == null },
      " · offscreen".takeIf { offscreen },
    ).joinToString("")
    out += AccessibilityLegendEntry(
      line = description.line + suffix,
      details = description.details,
      bounds = rect,
      problem = description.problem,
      order = position?.toDouble(),
      offscreen = offscreen,
      container = description.container,
    )
  }
  node.children.forEach { child ->
    collectSemantics(
      node = child,
      provider = provider,
      rootId = rootId,
      walk = walk,
      order = order,
      out = out,
    )
  }
}

private fun describeCompose(node: SemanticsNode, info: AccessibilityNodeInfo?): Description? {
  val config = node.config
  val description = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(", ")
  val editable = config.getOrNull(SemanticsProperties.EditableText)
  val text = config.getOrNull(SemanticsProperties.Text)?.joinToString(", ") { it.text }
  val label = description.toLabel() ?: text.toLabel()
  val isField = editable != null || config.contains(SemanticsActions.SetText)
  val role = when {
    config.contains(SemanticsProperties.Heading) -> "Heading"
    isField -> "EditText"
    else -> config.getOrNull(SemanticsProperties.Role)?.toString()
      ?: info?.className?.toString()?.let(::classNameRole)
  }
  val actionable = config.contains(SemanticsActions.OnClick) ||
    config.contains(SemanticsActions.OnLongClick) ||
    config.contains(SemanticsProperties.ToggleableState) ||
    config.contains(SemanticsActions.SetProgress)
  val paneTitle = config.getOrNull(SemanticsProperties.PaneTitle)
  val liveRegion = config.getOrNull(SemanticsProperties.LiveRegion)?.toString()
  val range = config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
  val announced = label != null || actionable || role != null || isField ||
    paneTitle != null || liveRegion != null || range != null
  if (!announced) return null
  if (label == null && !actionable && paneTitle != null) return paneDescription(paneTitle)
  val stateDescription = config.getOrNull(SemanticsProperties.StateDescription)
    ?.takeUnlessSameValue(range?.current)
  val states = buildList {
    if (stateDescription == null) {
      when (config.getOrNull(SemanticsProperties.ToggleableState)) {
        ToggleableState.On -> add("checked")
        ToggleableState.Off -> add("not checked")
        ToggleableState.Indeterminate -> add("partially checked")
        null -> Unit
      }
      if (config.getOrNull(SemanticsProperties.Selected) == true) add("selected")
    } else {
      add(stateDescription)
    }
    if (config.contains(SemanticsProperties.Disabled)) add("disabled")
    config.getOrNull(SemanticsActions.OnClick)?.label?.let { add("click: $it") }
  }
  val details = buildList {
    if (range != null) {
      val (min, max) = range.range.start to range.range.endInclusive
      add(rangeValue(current = range.current, min = min, max = max))
    }
    if (isField) {
      val hint = (if (Build.VERSION.SDK_INT >= 26) info?.hintText else null).toLabel()
      if (hint != null && hint != label) add("hint: \"$hint\"")
      if (!editable?.text.isNullOrEmpty()) add("value: \"${editable.text.oneLine()}\"")
      config.getOrNull(SemanticsProperties.Error)?.let { add("⚠ error: \"${it.oneLine()}\"") }
      if (config.contains(SemanticsProperties.Password)) add("password (characters hidden)")
    }
    paneTitle?.let { add("pane title: \"$it\", announced when shown") }
    liveRegion?.let { add("live region: $it, changes are announced") }
  }
  val problem = actionable && label == null
  return Description(
    line = legendLine(
      label = label,
      problem = problem,
      role = role,
      actionable = actionable,
      states = states,
    ),
    details = details,
    problem = problem,
  )
}

// endregion

private val INTERACTIVE_ROLES = setOf(
  "Button", "Checkbox", "Switch", "RadioButton", "Tab", "DropdownList", "EditText", "Slider",
)

private fun legendLine(
  label: String?,
  problem: Boolean,
  role: String?,
  actionable: Boolean,
  states: List<String>,
): String = listOfNotNull(
  if (problem) "⚠ NO LABEL" else label?.let { "\"$it\"" },
  role,
  "clickable".takeIf { actionable && role !in INTERACTIVE_ROLES },
  states.takeIf { it.isNotEmpty() }?.joinToString(", "),
).joinToString(" · ")

private fun paneDescription(paneTitle: String) = Description(
  line = "",
  details = emptyList(),
  focusable = false,
  container = "pane \"$paneTitle\": announced when shown",
)

private fun rangeValue(current: Float, min: Float, max: Float): String =
  "value: ${current.format()} of ${min.format()}–${max.format()}"

/** Null when the state description only repeats the [value] of a slider or progress bar. */
private fun String.takeUnlessSameValue(value: Float?): String? =
  takeUnless { value != null && toFloatOrNull()?.let { abs(it - value) < 0.01f } == true }

private fun CharSequence?.toLabel(): String? =
  this?.toString()?.oneLine()?.takeIf { it.isNotBlank() }

private fun String.oneLine(): String = replace('\n', ' ')

private fun Float.format(): String =
  if (abs(this - roundToInt()) < 0.01f) "${roundToInt()}" else "%.1f".format(Locale.ROOT, this)
