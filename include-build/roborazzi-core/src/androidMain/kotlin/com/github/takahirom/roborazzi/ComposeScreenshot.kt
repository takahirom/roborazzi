package com.github.takahirom.roborazzi

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.Window
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.InternalTestApi
import androidx.compose.ui.window.DialogWindowProvider
import androidx.concurrent.futures.ResolvableFuture
import kotlin.math.roundToInt

fun SemanticsNode.fetchImage(recordOptions: RoborazziOptions.RecordOptions): Bitmap? {
  val node = this
  val view = (node.root as ViewRootForTest).view
  //  From AOSP
  // If we are in dialog use its window to capture the bitmap
  val dialogParentNodeMaybe = node.findClosestParentNode(includeSelf = true) {
    it.config.contains(SemanticsProperties.IsDialog)
  } ?: if (this.isRoot) {
    node.children.find {
      it.config.contains(SemanticsProperties.IsDialog)
    }
  } else {
    null
  }
  var dialogWindow: Window? = null
  if (dialogParentNodeMaybe != null) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
      // TODO(b/163023027)
      throw IllegalArgumentException("Cannot currently capture dialogs on API lower than 28!")
    }

    dialogWindow = findDialogWindowProviderInParent(view)?.window
      ?: throw IllegalArgumentException(
        "Could not find a dialog window provider to capture its bitmap"
      )
  }

  val windowToUse = dialogWindow ?: view.context.getActivityWindow()

  val nodeBounds = node.boundsInRoot
  val nodeBoundsRect = Rect(
    nodeBounds.left.roundToInt(),
    nodeBounds.top.roundToInt(),
    nodeBounds.right.roundToInt(),
    nodeBounds.bottom.roundToInt()
  )
  val locationInWindow = IntArray(2)
  view.getLocationInWindow(locationInWindow)
  nodeBoundsRect.offset(locationInWindow[0], locationInWindow[1])
  if (dialogWindow != null) {
    return windowToUse.decorView.fetchImage(recordOptions = recordOptions)
      ?.crop(nodeBoundsRect, recordOptions)
  }

  // Experimental workaround to avoid content overlapping when capturing the bitmap.
  val actionBarWorkaroundIsOn = getSystemProperty(
    "roborazzi.compose.actionbar.overlap.fix",
    "true"
  ) == "true"

  // For SDK 35 and above, if we have an ActionBar scenario
  // we will draw the bitmap directly from the ComposeView to avoid content overlapping.
  val shouldUseComposeCapture = Build.VERSION.SDK_INT >= 35 &&
    windowToUse.hasFeature(Window.FEATURE_ACTION_BAR) &&
    actionBarWorkaroundIsOn
  if (shouldUseComposeCapture) {
    roborazziReportLog(
      "Using ComposeView.draw to capture the bitmap to avoid content overlap issues with the ActionBar.\n" +
        "Window-based capture (which typically provides higher fidelity) can cause these overlap issues when an ActionBar is present.\n" +
        "We recommend setting the theme using <application android:theme=\"@android:style/Theme.Material.NoActionBar\" /> in your test/AndroidManifest.xml to fix this.\n" +
        "If you are intentionally using an ActionBar, you can disable this workaround by setting the gradle property 'roborazzi.compose.actionbar.overlap.fix' to false.\n" +
        "This problem is tracked in https://issuetracker.google.com/issues/383368165"
    )
    // Capture bitmap from ComposeView using generateBitmapFromDraw and then crop.
    val destBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    val bitmapFuture = ResolvableFuture.create<Bitmap>()
    view.generateBitmapFromDraw(destBitmap, bitmapFuture)
    val fullBitmap = bitmapFuture.get()
    return fullBitmap?.crop(nodeBoundsRect, recordOptions)
  }

  return windowToUse.decorView.fetchImage(recordOptions = recordOptions)
    ?.crop(nodeBoundsRect, recordOptions)
}

/**
 * From AOSP
 * Executes [selector] on every parent of this [SemanticsNode] and returns the closest
 * [SemanticsNode] to return `true` from [selector] or null if [selector] returns false
 * for all ancestors.
 *
 * @param includeSelf Whether it should include self into the search.
 */
internal fun SemanticsNode.findClosestParentNode(
  includeSelf: Boolean = false,
  selector: (SemanticsNode) -> Boolean
): SemanticsNode? {
  var currentParent = if (includeSelf) this else parent
  while (currentParent != null) {
    if (selector(currentParent)) {
      return currentParent
    } else {
      currentParent = currentParent.parent
    }
  }

  return null
}


fun Bitmap.crop(rect: Rect, recordOptions: RoborazziOptions.RecordOptions): Bitmap? {
  if (rect.width() == 0 || rect.height() == 0) {
    return null
  }
  if (
    rect.left == 0 && rect.top == 0 &&
    rect.width() == this.width && rect.height() == this.height
  ) {
    return this
  }
  val croppedBitmap =
    Bitmap.createBitmap(rect.width(), rect.height(), recordOptions.pixelBitConfig.toBitmapConfig())

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