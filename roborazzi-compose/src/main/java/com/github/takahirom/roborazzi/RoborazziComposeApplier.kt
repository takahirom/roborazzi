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

interface RoborazziComposeApplier

@ExperimentalRoborazziApi
interface RoborazziComposeActivityScenarioApplier : RoborazziComposeApplier {
  fun applyToActivityScenario(scenario: ActivityScenario<out Activity>)
}

@ExperimentalRoborazziApi
interface RoborazziComposeComposableApplier : RoborazziComposeApplier {
  fun applyToComposable(content: @Composable () -> Unit): @Composable () -> Unit
}


@ExperimentalRoborazziApi
class RoborazziComposeApplierBuilder {
  private val activityScenarioAppliers =
    mutableListOf<RoborazziComposeActivityScenarioApplier>()
  private val composableAppliers = mutableListOf<RoborazziComposeComposableApplier>()

  fun with(applier: RoborazziComposeApplier): RoborazziComposeApplierBuilder {
    if (applier is RoborazziComposeActivityScenarioApplier) {
      activityScenarioAppliers.add(applier)
    }
    if (applier is RoborazziComposeComposableApplier) {
      composableAppliers.add(applier)
    }
    return this
  }

  fun sized(widthDp: Int = 0, heightDp: Int = 0): RoborazziComposeApplierBuilder {
    return with(RoborazziComposeSizeApplier(widthDp, heightDp))
  }

  fun colored(
    showBackground: Boolean,
    backgroundColor: Long = 0L
  ): RoborazziComposeApplierBuilder {
    return with(RoborazziComposeBackgroundApplier(showBackground, backgroundColor))
  }

  @InternalRoborazziApi
  fun apply(
    scenario: ActivityScenario<out Activity>,
    content: @Composable () -> Unit
  ): @Composable () -> Unit {
    activityScenarioAppliers.forEach { it.applyToActivityScenario(scenario) }
    var appliedContent = content
    composableAppliers.forEach { applier ->
      appliedContent = applier.applyToComposable(appliedContent)
    }
    return {
      appliedContent()
    }
  }

  @ExperimentalRoborazziApi
  data class RoborazziComposeSizeApplier(val widthDp: Int, val heightDp: Int) :
    RoborazziComposeActivityScenarioApplier,
    RoborazziComposeComposableApplier {
    override fun applyToActivityScenario(scenario: ActivityScenario<out Activity>) {
      scenario.onActivity { activity ->
        activity.setDisplaySize(widthDp = widthDp, heightDp = heightDp)
      }
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

    override fun applyToComposable(content: @Composable () -> Unit): @Composable () -> Unit {
      /**
       * WARNING:
       * For this to work, it requires that the Display is within the widthDp and heightDp dimensions
       * You can ensure that by calling [Activity.setDisplaySize] before
       */
      val modifier = when {
        widthDp > 0 && heightDp > 0 -> Modifier.size(widthDp.dp, heightDp.dp)
        widthDp > 0 -> Modifier.width(widthDp.dp)
        heightDp > 0 -> Modifier.height(heightDp.dp)
        else -> Modifier
      }
      return {
        Box(modifier = modifier) {
          content()
        }
      }
    }
  }
}

@ExperimentalRoborazziApi
data class RoborazziComposeBackgroundApplier(
  val showBackground: Boolean,
  val backgroundColor: Long
) : RoborazziComposeActivityScenarioApplier {
  override fun applyToActivityScenario(scenario: ActivityScenario<out Activity>) {
    when (showBackground) {
      false -> {
        scenario.onActivity { activity ->
          activity.window.decorView.setBackgroundColor(Color.TRANSPARENT)
        }
      }

      true -> {
        val color = when (backgroundColor != 0L) {
          true -> backgroundColor.toInt()
          false -> Color.WHITE
        }
        scenario.onActivity { activity ->
          activity.window.decorView.setBackgroundColor(color)
        }
      }
    }
  }
}
