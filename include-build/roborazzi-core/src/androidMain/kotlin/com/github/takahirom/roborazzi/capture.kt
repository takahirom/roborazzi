package com.github.takahirom.roborazzi

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.espresso.Root
import androidx.test.espresso.util.HumanReadables

val hasCompose = try {
  Class.forName("androidx.compose.ui.platform.AbstractComposeView")
  true
} catch (e: Exception) {
  false
}

private fun Rect.toRoboRect(): RoboRect = RoboRect(left, top, right, bottom)

private fun RoboRect.toAndroidRect(): Rect = Rect(left, top, right, bottom)

sealed interface RoboComponent : RoboComponentTree {
  override val children: List<RoboComponent>
  val image: Bitmap?
  val rect: Rect
  val text: String
  val accessibilityText: String

  class Screen(
    rootsOrderByDepth: List<Root>,
    roborazziOptions: RoborazziOptions,
  ) : RoboComponent {
    override val width: Int = rootsOrderByDepth.maxOfOrNull {
      it.decorView.width
    } ?: 0
    override val height: Int = rootsOrderByDepth.maxOfOrNull {
      it.decorView.height
    } ?: 0
    override val image: Bitmap? = if (roborazziOptions.shouldTakeBitmap) {
      val bitmap = Bitmap.createBitmap(width, height, roborazziOptions.recordOptions.pixelBitConfig.toBitmapConfig())
      val canvas = Canvas(bitmap)
      val screenDecorView = rootsOrderByDepth.first().decorView
      rootsOrderByDepth.forEach { root ->
        val layoutParams = root.windowLayoutParams.get()
        val decorView = root.decorView
        if ((layoutParams.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0) {
          val alpha = (layoutParams.dimAmount * 255).toInt()
          val color = Color.argb(alpha, 0, 0, 0)
          val paint = Paint().apply { this.color = color }
          canvas.drawRect(Rect(0, 0, width, height), paint)
        }
        val outRect = Rect()
        Gravity.apply(
          layoutParams.gravity,
          root.decorView.width,
          root.decorView.height,
          Rect(0, 0, screenDecorView.width, screenDecorView.height),
          layoutParams.x,
          layoutParams.y,
          outRect
        )
        decorView.fetchImage(
          recordOptions = roborazziOptions.recordOptions,
        )?.let {
          canvas.drawBitmap(it, outRect.left.toFloat(), outRect.top.toFloat(), null)
        }
      }
      bitmap
    } else {
      null
    }
    override val bounds: RoboRect = run {
      val rect = Rect()
      rootsOrderByDepth.firstOrNull()?.decorView?.getGlobalVisibleRect(rect)
      rect.toRoboRect()
    }
    override val rect: Rect = bounds.toAndroidRect()
    override val children: List<RoboComponent> by lazy {
      val screenDecorView = rootsOrderByDepth.first().decorView
      rootsOrderByDepth.map { root ->
        val layoutParams = root.windowLayoutParams.get()
        val decorView = root.decorView
        val outRect = Rect()
        Gravity.apply(
          layoutParams.gravity,
          root.decorView.width,
          root.decorView.height,
          Rect(0, 0, screenDecorView.width, screenDecorView.height),
          layoutParams.x,
          layoutParams.y,
          outRect
        )
        View(
          decorView, roborazziOptions, outRect
        )
      }
    }
    override val text: String = "Screen"
    override val accessibilityText: String = ""
    override val visibility: Visibility = Visibility.Visible
    override val properties: Map<String, String> = emptyMap()
    override val actions: List<String> = emptyList()
    override val flags: List<String> = emptyList()
    override val testTag: String? = null
    override val treeType: RoboComponentTreeType = RoboComponentTreeType.Screen
  }

  class View(
    view: android.view.View,
    roborazziOptions: RoborazziOptions,
    val windowOffset: Rect = Rect(),
  ) : RoboComponent {
    override val width: Int = view.width
    override val height: Int = view.height
    override val image: Bitmap? = if (roborazziOptions.shouldTakeBitmap) {
      view.fetchImage(
        recordOptions = roborazziOptions.recordOptions,
      )
    } else {
      null
    }
    override val bounds: RoboRect = run {
      val rect = Rect()
      view.getGlobalVisibleRect(rect)
      rect.apply { offset(windowOffset.left, windowOffset.top) }.toRoboRect()
    }
    override val rect: Rect = bounds.toAndroidRect()
    override val children: List<RoboComponent> = roborazziOptions
      .captureType
      .roboComponentChildVisitor(view, roborazziOptions, windowOffset)

    val id: Int = view.id

    val idResourceName: String? = if (0xFFFFFFFF.toInt() == view.id) {
      null
    } else {
      try {
        view.resources.getResourceName(view.id)
      } catch (e: Exception) {
        null
      }
    }

    override val treeType: RoboComponentTreeType = RoboComponentTreeType.View
    override val className: String? = view.javaClass.name
    override val resourceId: String? = idResourceName
    override val text: String = HumanReadables.describe(view)

    // TODO: Support other accessibility information
    override val accessibilityText: String = run {
      buildString {
        val contentDescription = view.contentDescription?.toString()
        val text = (view as? TextView)?.text?.toString()
        if (contentDescription != null) {
          appendLine("Content Description: \"$contentDescription\"")
        } else if (text != null) {
          appendLine("Text: \"$text\"")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          val stateDescription = view.stateDescription?.toString()

          if (stateDescription != null) {
            appendLine("State Description: \"$stateDescription\"")
          }
        }

        val clickable = view.hasOnClickListeners() && view.isClickable
        if (clickable) {
          appendLine("Clickable: \"true\"")
        }


        val isImportantForAccessibility = view.isImportantForAccessibility
        if (isImportantForAccessibility) {
          appendLine("isImportantForAccessibility: \"true\"")
        }
      }
    }

    override val visibility: Visibility = when (view.visibility) {
      android.view.View.VISIBLE -> Visibility.Visible
      android.view.View.GONE -> Visibility.Gone
      else -> Visibility.Invisible
    }

    // Lazy: the structured extraction is only needed by the UI tree dump feature
    // (off by default). Computing it eagerly would pay the cost on every capture's
    // root node even when the feature is disabled.
    override val properties: Map<String, String> by lazy {
      buildMap {
        (view as? TextView)?.text?.toString()?.let { put("Text", it) }
        view.contentDescription?.toString()?.let { put("ContentDescription", it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          view.stateDescription?.toString()?.let { put("StateDescription", it) }
        }
      }
    }

    override val actions: List<String> by lazy {
      if (view.hasOnClickListeners() && view.isClickable) listOf("OnClick") else emptyList()
    }

    override val flags: List<String> by lazy {
      buildList {
        if (view.isClickable) add("Clickable")
        if (view.isFocused) add("Focused")
        if (!view.isEnabled) add("Disabled")
      }.sorted()
    }

    override val testTag: String? = null
  }

  class Compose(
    node: SemanticsNode,
    roborazziOptions: RoborazziOptions,
    val windowOffset: Rect = Rect(),
  ) : RoboComponent {
    override val width: Int = node.layoutInfo.width
    override val height: Int = node.layoutInfo.height
    override val image: Bitmap? = if (roborazziOptions.shouldTakeBitmap) {
      node.fetchImage(recordOptions = roborazziOptions.recordOptions)
    } else {
      null
    }
    override val children: List<RoboComponent> = roborazziOptions
      .captureType
      .roboComponentChildVisitor(node, roborazziOptions, windowOffset)
    // Lazy: printToString() walks the semantics config and is only needed by the
    // UI tree dump feature (off by default), so it must not run on every capture.
    override val text: String by lazy { node.printToString() }
    override val accessibilityText: String = run {
      buildString {
        val contentDescription = node.config.getOrNull(SemanticsProperties.ContentDescription)
        if (contentDescription != null) {
          appendLine("Content Description: \"${contentDescription.joinToString(", ")}\"")
        }
        val text = node.config.getOrNull(SemanticsProperties.Text)
        if (text != null) {
          appendLine("Text: \"${text.joinToString(", ")}\"")
        }
        val stateDescription = node.config.getOrNull(SemanticsProperties.StateDescription)
        if (stateDescription != null) {
          appendLine("State Description: \"${stateDescription}\"")
        }
        val onClickLabel = node.config.getOrNull(SemanticsActions.OnClick)
        if (onClickLabel != null) {
          appendLine("On Click: \"${onClickLabel}\"")
        }
        val progress = node.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
        if (progress != null) {
          appendLine("Progress: \"${progress}\"")
        }
        val customActions = node.config.getOrNull(SemanticsActions.CustomActions)
        if (customActions != null) {
          for (action in customActions) {
            appendLine("Custom Action: \"${action.label}\"")
          }
        }
        val disabled = node.config.getOrNull(SemanticsProperties.Disabled)
        if (disabled != null) {
          appendLine("Disabled: \"true\"")
        }
      }
    }
    override val visibility: Visibility = Visibility.Visible
    override val testTag: String? = node.config.getOrNull(SemanticsProperties.TestTag)

    override val bounds: RoboRect = run {
      val rect = Rect()
      val boundsInWindow = node.boundsInWindow
      rect.set(boundsInWindow.toAndroidRect())
      rect.apply { offset(windowOffset.left, windowOffset.top) }.toRoboRect()
    }
    override val rect: Rect = bounds.toAndroidRect()

    // Lazy: the structured extraction is only needed by the UI tree dump feature
    // (off by default). Computing it eagerly would pay the cost on every capture's
    // root node even when the feature is disabled.
    override val properties: Map<String, String> by lazy { node.config.toRoboProperties() }
    override val actions: List<String> by lazy { node.config.toRoboActions() }
    override val flags: List<String> by lazy { node.config.toRoboFlags() }
    override val treeType: RoboComponentTreeType = RoboComponentTreeType.Compose
  }

  companion object {
    internal val defaultChildVisitor: (Any, RoborazziOptions, Rect) -> List<RoboComponent> =
      { platformNode: Any, roborazziOptions: RoborazziOptions, windowOffset: Rect ->
        when {
          hasCompose && platformNode is androidx.compose.ui.platform.AbstractComposeView -> {
            (platformNode.getChildAt(0) as? ViewRootForTest)?.semanticsOwner?.rootSemanticsNode?.let {
              listOf(Compose(it, roborazziOptions, windowOffset))
            } ?: listOf()
          }

          platformNode is ViewGroup -> {
            (0 until platformNode.childCount).map {
              View(
                platformNode.getChildAt(it), roborazziOptions, windowOffset
              )
            }
          }

          hasCompose && platformNode is SemanticsNode -> {
            platformNode.children.map {
              Compose(
                it, roborazziOptions, windowOffset
              )
            }
          }

          else -> {
            listOf()
          }
        }
      }
  }
}

fun withViewId(@IdRes id: Int): (RoboComponent) -> Boolean {
  return { roboComponent ->
    when (roboComponent) {
      is RoboComponent.Screen -> false
      is RoboComponent.Compose -> false
      is RoboComponent.View -> roboComponent.id == id
    }
  }
}

fun withComposeTestTag(testTag: String): (RoboComponent) -> Boolean {
  return { roboComponent ->
    when (roboComponent) {
      is RoboComponent.Screen -> false
      is RoboComponent.Compose -> testTag == roboComponent.testTag
      is RoboComponent.View -> false
    }
  }
}

/**
 * Internal capture type used only to build the full UI tree for the JSON
 * sidecar. It never takes bitmaps ([shouldTakeScreenshot] returns false, so
 * child [RoboComponent] construction skips the per-node bitmap fetch) yet it
 * traverses the whole hierarchy via [RoboComponent.defaultChildVisitor]. This
 * lets us describe the tree without paying the child-bitmap cost and without
 * changing the behavior of the actual screenshot capture.
 */
@InternalRoborazziApi
class UiTreeTraversalCaptureType : RoborazziOptions.CaptureType {
  override fun shouldTakeScreenshot(): Boolean = false
}

internal val RoborazziOptions.CaptureType.roboComponentChildVisitor: (Any, RoborazziOptions, Rect) -> List<RoboComponent>
  get() {
    return when (this) {
      is Dump -> RoboComponent.defaultChildVisitor
      is UiTreeTraversalCaptureType -> RoboComponent.defaultChildVisitor
      is RoborazziOptions.CaptureType.Screenshot -> { _, _, _ -> listOf() }
      else -> { _, _, _ -> listOf() }
    }
  }

@InternalRoborazziApi
sealed interface QueryResult {
  object Disabled : QueryResult
  data class Enabled(val matched: Boolean) : QueryResult

  companion object {
    fun of(component: RoboComponent, query: ((RoboComponent) -> Boolean)?): QueryResult {
      if (query == null) return Disabled
      return Enabled(query(component))
    }
  }
}

