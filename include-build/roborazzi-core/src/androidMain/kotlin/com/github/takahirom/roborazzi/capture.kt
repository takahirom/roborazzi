package com.github.takahirom.roborazzi

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Point
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
import androidx.core.graphics.plus
import androidx.test.espresso.Root
import androidx.test.espresso.util.HumanReadables

enum class Visibility {
  Visible, Gone, Invisible;
}

val hasCompose = try {
  Class.forName("androidx.compose.ui.platform.AbstractComposeView")
  true
} catch (e: Exception) {
  false
}

sealed interface RoboComponent {
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
        if ((layoutParams.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND) !== 0) {
          val alpha = (layoutParams.dimAmount * 255).toInt()
          val color = Color.argb(alpha, 0, 0, 0)
          decorView.setBackgroundColor(color)
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
    override val rect: Rect = run {
      val rect = Rect()
      rootsOrderByDepth.firstOrNull()?.decorView?.getGlobalVisibleRect(rect)
      rect
    }
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
    override val rect: Rect = run {
      val rect = Rect()
      view.getGlobalVisibleRect(rect)
      rect + Point(windowOffset.left, windowOffset.top)
    }
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
    override val text: String = node.printToString()
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
    val testTag: String? = node.config.getOrNull(SemanticsProperties.TestTag)

    override val rect: Rect = run {
      val rect = Rect()
      val boundsInWindow = node.boundsInWindow
      rect.set(boundsInWindow.toAndroidRect())
      rect + Point(windowOffset.left, windowOffset.top)
    }
  }

  val image: Bitmap?
  val rect: Rect
  val children: List<RoboComponent>
  val text: String
  val accessibilityText: String
  val visibility: Visibility
  val width: Int
  val height: Int

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

internal val RoborazziOptions.CaptureType.roboComponentChildVisitor: (Any, RoborazziOptions, Rect) -> List<RoboComponent>
  get() {
    return when (this) {
      is Dump -> RoboComponent.defaultChildVisitor
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

