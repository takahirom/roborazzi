package com.github.takahirom.roborazzi.sample

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.RoboComponent
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.fetchRobolectricWindowRoots
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A popup window (PopupWindow, Spinner drop-down, Compose Popup/DropdownMenu) is a sub-window:
 * its layout params x/y are relative to its parent window, not to the screen.
 *
 * These tests capture the whole screen and check that the popup is composited right below its
 * anchor, which is where the framework puts it. Colors are used instead of absolute coordinates
 * so the assertions do not depend on where the parent window happens to sit.
 *
 * https://github.com/takahirom/roborazzi/issues/921
 */
@OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], qualifiers = RobolectricDeviceQualifiers.NexusOne)
class PopupWindowCaptureTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val anchorColor = Color.GREEN
  private val popupColor = Color.MAGENTA

  @Test
  fun popupWindowInDialogIsDrawnBelowItsAnchor() {
    lateinit var anchorSize: Pair<Int, Int>
    ActivityScenario.launch(MainActivity::class.java).onActivity { activity ->
      val anchor = anchorView(activity)
      AlertDialog.Builder(activity)
        .setTitle("Dialog with a popup")
        .setView(FrameLayout(activity).apply { addView(anchor) })
        .show()
      shadowOf(activity.mainLooper).idle()

      PopupWindow(popupContentView(activity), POPUP_WIDTH, POPUP_HEIGHT)
        .showAsDropDown(anchor)
      shadowOf(activity.mainLooper).idle()
      anchorSize = anchor.width to anchor.height
    }

    captureScreenRoboImage()
    val screen = screenComponent()
    val popupRect = assertPopupIsBelowAnchor(requireNotNull(screen.image), anchorSize)
    // The tree traversal used by the ui tree dump has to agree with the composited image.
    assertEquals("popup window rect in the component tree", popupRect, screen.children.last().rect)
  }

  // A popup anchored in the activity window must keep working: its parent window is the screen.
  @Test
  fun popupWindowInActivityIsDrawnBelowItsAnchor() {
    lateinit var anchorSize: Pair<Int, Int>
    ActivityScenario.launch(MainActivity::class.java).onActivity { activity ->
      val anchor = anchorView(activity)
      activity.findViewById<ViewGroup>(android.R.id.content).addView(anchor)
      shadowOf(activity.mainLooper).idle()

      PopupWindow(popupContentView(activity), POPUP_WIDTH, POPUP_HEIGHT)
        .showAsDropDown(anchor)
      shadowOf(activity.mainLooper).idle()
      anchorSize = anchor.width to anchor.height
    }

    captureScreenRoboImage()
    val screen = screenComponent()
    val popupRect = assertPopupIsBelowAnchor(requireNotNull(screen.image), anchorSize)
    assertEquals("popup window rect in the component tree", popupRect, screen.children.last().rect)
  }

  @Test
  fun composeDropdownMenuInDialogIsDrawnBelowItsAnchor() {
    composeTestRule.setContent {
      Dialog(onDismissRequest = {}) {
        Box(
          Modifier
            .size(ANCHOR_WIDTH_DP.dp, ANCHOR_HEIGHT_DP.dp)
            .background(ComposeColor(anchorColor))
        ) {
          DropdownMenu(expanded = true, onDismissRequest = {}) {
            DropdownMenuItem(
              text = {
                Box(
                  Modifier
                    .size(MENU_ITEM_SIZE_DP.dp)
                    .background(ComposeColor(popupColor))
                )
              },
              onClick = {}
            )
          }
        }
      }
    }
    composeTestRule.waitForIdle()

    captureScreenRoboImage()
    assertDropdownMenuIsAlignedWithAnchor(requireNotNull(screenComponent().image))
  }

  // A Compose DropdownMenu anchored in the activity window must keep working too.
  @Test
  fun composeDropdownMenuInActivityIsDrawnBelowItsAnchor() {
    composeTestRule.setContent {
      Box(
        Modifier
          .size(ANCHOR_WIDTH_DP.dp, ANCHOR_HEIGHT_DP.dp)
          .background(ComposeColor(anchorColor))
      ) {
        DropdownMenu(expanded = true, onDismissRequest = {}) {
          DropdownMenuItem(
            text = {
              Box(
                Modifier
                  .size(MENU_ITEM_SIZE_DP.dp)
                  .background(ComposeColor(popupColor))
              )
            },
            onClick = {}
          )
        }
      }
    }
    composeTestRule.waitForIdle()

    captureScreenRoboImage()
    assertDropdownMenuIsAlignedWithAnchor(requireNotNull(screenComponent().image))
  }

  private fun assertDropdownMenuIsAlignedWithAnchor(bitmap: Bitmap) {
    val anchorRect = requireNotNull(boundingBoxOf(bitmap, anchorColor)) {
      "anchor not found in the captured screen"
    }
    val menuItemRect = requireNotNull(boundingBoxOf(bitmap, popupColor)) {
      "dropdown menu item not found in the captured screen"
    }
    // The menu is placed against the anchor, not against the screen origin.
    // Menu padding keeps it from lining up exactly with the anchor, so check the band it falls in.
    assertTrue(
      "dropdown menu $menuItemRect should be aligned with its anchor $anchorRect",
      menuItemRect.left >= anchorRect.left &&
        menuItemRect.right <= anchorRect.right &&
        menuItemRect.top >= anchorRect.top
    )
  }

  private fun assertPopupIsBelowAnchor(bitmap: Bitmap, anchorSize: Pair<Int, Int>): Rect {
    val anchorRect = boundingBoxOf(bitmap, anchorColor)
    val popupRect = boundingBoxOf(bitmap, popupColor)
    assertNotNull("anchor not found in the captured screen", anchorRect)
    assertNotNull("popup not found in the captured screen", popupRect)
    assertEquals("anchor width", anchorSize.first, anchorRect!!.width())
    assertEquals("popup left", anchorRect.left, popupRect!!.left)
    assertEquals("popup top", anchorRect.bottom, popupRect.top)
    assertEquals("popup width", POPUP_WIDTH, popupRect.width())
    assertEquals("popup height", POPUP_HEIGHT, popupRect.height())
    return popupRect
  }

  private fun screenComponent(): RoboComponent.Screen = RoboComponent.Screen(
    rootsOrderByDepth = fetchRobolectricWindowRoots(),
    roborazziOptions = RoborazziOptions(),
  )

  /** Bounding box of every pixel painted with [color], or null when there is none. */
  private fun boundingBoxOf(bitmap: Bitmap, color: Int): Rect? {
    var left = Int.MAX_VALUE
    var top = Int.MAX_VALUE
    var right = Int.MIN_VALUE
    var bottom = Int.MIN_VALUE
    for (y in 0 until bitmap.height) {
      for (x in 0 until bitmap.width) {
        if (bitmap.getPixel(x, y) == color) {
          if (x < left) left = x
          if (y < top) top = y
          if (x >= right) right = x + 1
          if (y >= bottom) bottom = y + 1
        }
      }
    }
    return if (left == Int.MAX_VALUE) null else Rect(left, top, right, bottom)
  }

  private fun anchorView(activity: Activity): View = TextView(activity).apply {
    setBackgroundColor(anchorColor)
    layoutParams = ViewGroup.LayoutParams(ANCHOR_WIDTH, ANCHOR_HEIGHT)
  }

  private fun popupContentView(activity: Activity): View = FrameLayout(activity).apply {
    setBackgroundColor(popupColor)
  }

  companion object {
    private const val ANCHOR_WIDTH = 200
    private const val ANCHOR_HEIGHT = 80
    private const val ANCHOR_WIDTH_DP = 100
    private const val ANCHOR_HEIGHT_DP = 40
    private const val POPUP_WIDTH = 300
    private const val POPUP_HEIGHT = 150
    private const val MENU_ITEM_SIZE_DP = 24
  }
}
