package com.github.takahirom.roborazzi

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.graphics.Color
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDisplay.getDefaultDisplay
import kotlin.math.roundToInt

fun captureSizedRoboImage(
  widthDp: Int,
  heightDp: Int,
  showBackground: Boolean,
  backgroundColor: Long,
  filePath: String,
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  content: @Composable () -> Unit,
) {
  if (!roborazziOptions.taskType.isEnabled()) return
  registerTransparentActivityToRobolectricIfNeeded()

  val activityScenario = ActivityScenario.launch(RoborazziTransparentActivity::class.java)

  activityScenario.use {
    val sizedPreview = activityScenario.createSizedPreview(
        widthDp = widthDp,
        heightDp = heightDp,
        preview = { content() }
      )

    activityScenario.setBackgroundColor(
      showBackground = showBackground,
      backgroundColor = backgroundColor
    )

    activityScenario.onActivity { activity ->
      activity.setContent(content = sizedPreview)

      val composeView = activity.window.decorView
        .findViewById<ViewGroup>(android.R.id.content)
        .getChildAt(0) as ComposeView

      val viewRootForTest = composeView.getChildAt(0) as ViewRootForTest
      viewRootForTest.view.captureRoboImage(filePath, roborazziOptions)
    }

    // Closing the activity is necessary to prevent memory leaks.
    // If multiple captureRoboImage calls occur in a single test,
    // they can lead to an activity leak.
  }
}

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

    true -> if (backgroundColor != 0L) {
      onActivity { activity ->
        activity.window.decorView.setBackgroundColor(backgroundColor.toInt())
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
  return result ?: throw IllegalStateException("Result was not set within the activity lambda")
}


private fun Activity.setDisplaySize(
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
private fun (@Composable () -> Unit).size(
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

private fun registerTransparentActivityToRobolectricIfNeeded() {
  try {
    val appContext: Application = ApplicationProvider.getApplicationContext()
    shadowOf(appContext.packageManager).addActivityIfNotPresent(
      ComponentName(
        appContext.packageName,
        RoborazziTransparentActivity::class.java.name,
      )
    )
  } catch (e: ClassNotFoundException) {
    // Configured to run even without Robolectric
    e.printStackTrace()
  }
}