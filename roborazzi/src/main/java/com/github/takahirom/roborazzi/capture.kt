package com.github.takahirom.roborazzi

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.espresso.util.HumanReadables
import org.robolectric.annotation.GraphicsMode
import org.robolectric.config.ConfigurationRegistry

internal val colors = listOf(
  0x3F9101,
  0x0E4A8E,
  0xBCBF01,
  0xBC0BA2,
  0x61AA0D,
  0x3D017A,
  0xD6A60A,
  0x7710A3,
  0xA502CE,
  0xeb5a00
)

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
  val image: Bitmap?

  class View(
    view: android.view.View,
    captureOptions: CaptureOptions,
  ) : RoboComponent {
    override val width: Int = view.width
    override val height: Int = view.height
    override val image: Bitmap? = if (captureOptions.shouldTakeBitmap) {
      view.fetchImage()
    } else {
      null
    }
    override val rect: Rect = run {
      val rect = Rect()
      view.getGlobalVisibleRect(rect)
      rect
    }
    override val children: List<RoboComponent> = captureOptions
      .captureType
      .roboComponentChildVisitor(view, captureOptions)

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
    override val visibility: Visibility = when (view.visibility) {
      android.view.View.VISIBLE -> Visibility.Visible
      android.view.View.GONE -> Visibility.Gone
      else -> Visibility.Invisible
    }
  }

  class Compose(
    node: SemanticsNode,
    captureOptions: CaptureOptions,
  ) : RoboComponent {
    override val width: Int = node.layoutInfo.width
    override val height: Int = node.layoutInfo.height
    override val image: Bitmap? = if (captureOptions.shouldTakeBitmap) {
      node.fetchImage()
    } else {
      null
    }
    override val children: List<RoboComponent> = captureOptions
      .captureType
      .roboComponentChildVisitor(node, captureOptions)
    override val text: String = node.printToString()
    override val visibility: Visibility = Visibility.Visible
    val testTag: String? = node.config.getOrNull(SemanticsProperties.TestTag)

    override val rect: Rect = run {
      val rect = Rect()
      val boundsInWindow = node.boundsInWindow
      rect.set(boundsInWindow.toAndroidRect())
      rect
    }
  }

  val rect: Rect
  val children: List<RoboComponent>
  val text: String
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
    internal val defaultChildVisitor: (Any, CaptureOptions) -> List<RoboComponent> =
      { platformNode: Any, captureOptions: CaptureOptions ->
        when {
          hasCompose && platformNode is androidx.compose.ui.platform.AbstractComposeView -> {
            (platformNode.getChildAt(0) as? ViewRootForTest)?.semanticsOwner?.rootSemanticsNode?.let {
              listOf(Compose(it, captureOptions))
            } ?: listOf()
          }

          platformNode is ViewGroup -> {
            (0 until platformNode.childCount).map {
              View(
                platformNode.getChildAt(it), captureOptions
              )
            }
          }

          hasCompose && platformNode is SemanticsNode -> {
            platformNode.children.map {
              Compose(
                it, captureOptions
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
      is RoboComponent.Compose -> false
      is RoboComponent.View -> roboComponent.id == id
    }
  }
}

fun withComposeTestTag(testTag: String): (RoboComponent) -> Boolean {
  return { roboComponent ->
    when (roboComponent) {
      is RoboComponent.Compose -> testTag == roboComponent.testTag
      is RoboComponent.View -> false
    }
  }
}

internal fun isNativeGraphicsEnabled() = try {
  Class.forName("org.robolectric.annotation.GraphicsMode")
  ConfigurationRegistry.get(GraphicsMode.Mode::class.java) == GraphicsMode.Mode.NATIVE
} catch (e: ClassNotFoundException) {
  false
}

data class CaptureOptions(
  val captureType: CaptureType = if (isNativeGraphicsEnabled()) CaptureType.Screenshot() else CaptureType.Dump(),
  val verifyOptions: VerifyOptions = VerifyOptions(),
  val recordOptions: RecordOptions = RecordOptions(),
) {
  sealed interface CaptureType {
    class Screenshot : CaptureType

    data class Dump(
      val takeScreenShot: Boolean = isNativeGraphicsEnabled(),
      val basicSize: Int = 600,
      val depthSlideSize: Int = 30,
      val query: ((RoboComponent) -> Boolean)? = null,
    ) : CaptureType
  }

  data class VerifyOptions(
    /**
     * This value determines the threshold of pixel change at which the diff image is output or not.
     * The value should be between 0 and 1
     */
    val changeThreshold: Double = 0.01
  )

  data class RecordOptions(
    val resizeImage: Double = 1.0
  )

  internal val shouldTakeBitmap: Boolean = when (captureType) {
    is CaptureType.Dump -> {
      if (captureType.takeScreenShot && !isNativeGraphicsEnabled()) {
        throw IllegalArgumentException("Please update Robolectric Robolectric 4.10 Alpha 1 and Add @GraphicsMode(GraphicsMode.Mode.NATIVE) or use takeScreenShot = false")
      }
      captureType.takeScreenShot
    }

    is CaptureType.Screenshot -> {
      if (!isNativeGraphicsEnabled()) {
        throw IllegalArgumentException("Please update Robolectric Robolectric 4.10 Alpha 1 and Add @GraphicsMode(GraphicsMode.Mode.NATIVE) or use CaptureType.Dump")
      }
      true
    }
  }
}

internal val CaptureOptions.CaptureType.roboComponentChildVisitor: (Any, CaptureOptions) -> List<RoboComponent>
  get() {
    return when (this) {
      is CaptureOptions.CaptureType.Dump -> RoboComponent.defaultChildVisitor
      is CaptureOptions.CaptureType.Screenshot -> { _, _ -> listOf() }
    }
  }

internal sealed interface QueryResult {
  object Disabled : QueryResult
  data class Enabled(val matched: Boolean) : QueryResult

  companion object {
    fun of(component: RoboComponent, query: ((RoboComponent) -> Boolean)?): QueryResult {
      if (query == null) return Disabled
      return Enabled(query(component))
    }
  }
}

