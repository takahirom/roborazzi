package com.github.takahirom.roborazzi

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.espresso.util.HumanReadables

@ExperimentalRoborazziApi
enum class Visibility {
  Visible, Gone, Invisible;
}

@ExperimentalRoborazziApi
sealed interface RoboComponent {
  class View(
    view: android.view.View,
    roborazziOptions: RoborazziOptions,
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
      rect
    }
    override val children: List<RoboComponent> = roborazziOptions
      .captureType
      .roboComponentChildVisitor(view, roborazziOptions)

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
      .roboComponentChildVisitor(node, roborazziOptions)
    override val text: String = node.printToString()
    override val accessibilityText: String = run {
      buildString {
        val contentDescription = node.config.getOrNull(SemanticsProperties.ContentDescription)
        val text = node.config.getOrNull(SemanticsProperties.Text)
        if (contentDescription != null) {
          appendLine("Content Description: \"${contentDescription.joinToString(", ")}\"")
        } else if (text != null) {
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
      }
    }
    override val visibility: Visibility = Visibility.Visible
    val testTag: String? = node.config.getOrNull(SemanticsProperties.TestTag)

    override val rect: Rect = run {
      val rect = Rect()
      val boundsInWindow = node.boundsInWindow
      rect.set(boundsInWindow.toAndroidRect())
      rect
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
    internal val defaultChildVisitor: (Any, RoborazziOptions) -> List<RoboComponent> =
      { platformNode: Any, roborazziOptions: RoborazziOptions ->
        when {
          hasCompose && platformNode is androidx.compose.ui.platform.AbstractComposeView -> {
            (platformNode.getChildAt(0) as? ViewRootForTest)?.semanticsOwner?.rootSemanticsNode?.let {
              listOf(Compose(it, roborazziOptions))
            } ?: listOf()
          }

          platformNode is ViewGroup -> {
            (0 until platformNode.childCount).map {
              View(
                platformNode.getChildAt(it), roborazziOptions
              )
            }
          }

          hasCompose && platformNode is SemanticsNode -> {
            platformNode.children.map {
              Compose(
                it, roborazziOptions
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