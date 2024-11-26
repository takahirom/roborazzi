package com.github.takahirom.roborazzi

import android.app.Activity
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDisplay.getDefaultDisplay
import kotlin.math.roundToInt

fun ActivityScenario<out Activity>.setBackgroundColor(
  showBackground: Boolean,
  backgroundColor: Long,
) {
  when (showBackground) {
    false -> {
      onActivity { activity ->
        activity.window.decorView.setBackgroundColor(Color.TRANSPARENT)
      }
    }

    true -> {
      val color = when (backgroundColor != 0L) {
        true -> backgroundColor.toInt()
        false -> Color.WHITE
      }
      onActivity { activity ->
        activity.window.decorView.setBackgroundColor(color)
      }
    }
  }
}

fun ActivityScenario<out Activity>.createSizedPreview(
  widthDp: Int,
  heightDp: Int,
  preview: @Composable () -> Unit
): @Composable () -> Unit {
  var result: (@Composable () -> Unit)? = null
  onActivity { activity ->
    activity.setDisplaySize(widthDp = widthDp, heightDp = heightDp)
    result = preview.size(widthDp = widthDp, heightDp = heightDp)
  }
  return result
    ?: throw IllegalStateException("The preview could not be sucessfully sized to widthDp = $widthDp and heightDp = $heightDp")
}


internal fun Activity.setDisplaySize(
  widthDp: Int,
  heightDp: Int
) {
  if (widthDp <= 0 && heightDp <= 0) return

  val display = shadowOf(getDefaultDisplay())
  val density = resources.displayMetrics.density
  if (widthDp > 0) {
    val widthPx = (widthDp * density).roundToInt()
    display.setWidth(widthPx)
  }
  if (heightDp > 0) {
    val heightPx = (heightDp * density).roundToInt()
    display.setHeight(heightPx)
  }
  recreate()
}

/**
 * WARNING:
 * For this to work, it requires that the Display is within the widthDp and heightDp dimensions
 * You can ensure that by calling [Activity.setDisplaySize] before
 */
internal fun (@Composable () -> Unit).size(
  widthDp: Int,
  heightDp: Int,
): @Composable () -> Unit {
  val resizedPreview = @Composable {
    val modifier = when {
      widthDp > 0 && heightDp > 0 -> Modifier.size(widthDp.dp, heightDp.dp)
      widthDp > 0 -> Modifier.width(widthDp.dp)
      heightDp > 0 -> Modifier.height(heightDp.dp)
      else -> Modifier
    }
    Box(modifier = modifier) {
      this@size()
    }
  }
  return resizedPreview
}