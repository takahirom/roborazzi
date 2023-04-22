package com.github.takahirom.roborazzi

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.Window
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.InternalTestApi
import androidx.compose.ui.window.DialogWindowProvider
import kotlin.math.roundToInt

fun SemanticsNode.fetchImage(fullScreen: Boolean): Bitmap? {
  val node = this
  val view = (node.root as ViewRootForTest).view

  val nodeBounds = node.boundsInRoot
  val nodeBoundsRect = Rect(
    nodeBounds.left.roundToInt(),
    nodeBounds.top.roundToInt(),
    nodeBounds.right.roundToInt(),
    nodeBounds.bottom.roundToInt()
  )
  return view.fetchImage(fullScreen = fullScreen)?.crop(nodeBoundsRect)
}


fun Bitmap.crop(rect: Rect): Bitmap? {
  val croppedBitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)

  val canvas = Canvas(croppedBitmap)
  canvas.drawBitmap(this, -rect.left.toFloat(), -rect.top.toFloat(), null)
  return croppedBitmap
}

// From AOSP: https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui-test/src/androidMain/kotlin/androidx/compose/ui/test/AndroidImageHelpers.android.kt;drc=d25dbd013c9d8fffee1264f98f8d201cdc45fa34
@OptIn(InternalTestApi::class)
private fun findNodePosition(
  node: SemanticsNode
): Offset {
  val view = (node.root as ViewRootForTest).view
  val locationOnScreen = intArrayOf(0, 0)
  view.getLocationOnScreen(locationOnScreen)
  val x = locationOnScreen[0]
  val y = locationOnScreen[1]

  return Offset(x.toFloat(), y.toFloat())
}

internal fun findDialogWindowProviderInParent(view: View): DialogWindowProvider? {
  if (view is DialogWindowProvider) {
    return view
  }
  val parent = view.parent ?: return null
  if (parent is View) {
    return findDialogWindowProviderInParent(parent)
  }
  return null
}

private fun Context.getActivityWindow(): Window {
  fun Context.getActivity(): Activity {
    return when (this) {
      is Activity -> this
      is ContextWrapper -> this.baseContext.getActivity()
      else -> throw IllegalStateException(
        "Context is not an Activity context, but a ${javaClass.simpleName} context. " +
          "An Activity context is required to get a Window instance"
      )
    }
  }
  return getActivity().window
}