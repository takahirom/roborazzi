package com.github.takahirom.roborazzi

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import org.robolectric.RuntimeEnvironment.setFontScale
import org.robolectric.RuntimeEnvironment.setQualifiers
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDisplay.getDefaultDisplay
import kotlin.math.roundToInt

@ExperimentalRoborazziApi
interface RoborazziComposeApplier

interface RoborazziComposeSetupApplier : RoborazziComposeApplier {
  fun apply()
}

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
  private val setupAppliers = mutableListOf<RoborazziComposeSetupApplier>()

  fun with(applier: RoborazziComposeApplier): RoborazziComposeApplierBuilder {
    if (applier is RoborazziComposeActivityScenarioApplier) {
      activityScenarioAppliers.add(applier)
    }
    if (applier is RoborazziComposeComposableApplier) {
      composableAppliers.add(applier)
    }
    if (applier is RoborazziComposeSetupApplier) {
      setupAppliers.add(applier)
    }
    return this
  }

  @InternalRoborazziApi
  fun apply(
    scenario: ActivityScenario<out Activity>,
    content: @Composable () -> Unit
  ): @Composable () -> Unit {
    setupAppliers.forEach { it.apply() }
    activityScenarioAppliers.forEach { it.applyToActivityScenario(scenario) }
    var appliedContent = content
    composableAppliers.forEach { applier ->
      appliedContent = applier.applyToComposable(appliedContent)
    }
    return {
      appliedContent()
    }
  }
}

@ExperimentalRoborazziApi
fun RoborazziComposeApplierBuilder.sized(widthDp: Int = 0, heightDp: Int = 0): RoborazziComposeApplierBuilder {
  return with(RoborazziComposeSizeApplier(widthDp, heightDp))
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

@ExperimentalRoborazziApi
fun RoborazziComposeApplierBuilder.colored(
  showBackground: Boolean,
  backgroundColor: Long = 0L
): RoborazziComposeApplierBuilder {
  return with(RoborazziComposeBackgroundApplier(showBackground, backgroundColor))
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

@ExperimentalRoborazziApi
fun RoborazziComposeApplierBuilder.uiMode(configurationUiMode: Int): RoborazziComposeApplierBuilder {
  return with(UiModeApplier(configurationUiMode))
}

@ExperimentalRoborazziApi
data class UiModeApplier(val uiMode: Int) :
  RoborazziComposeSetupApplier {
  override fun apply() {
    val nightMode =
      when (uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
        true -> "night"
        false -> "notnight"
      }
    setQualifiers("+$nightMode")
  }
}

@ExperimentalRoborazziApi
fun RoborazziComposeApplierBuilder.locale(bcp47LanguageTag: String): RoborazziComposeApplierBuilder {
  return with(LocaleApplier(bcp47LanguageTag))
}

@ExperimentalRoborazziApi
data class LocaleApplier(val locale: String) :
  RoborazziComposeSetupApplier {
  override fun apply() {
    val localeWithFallback = locale.ifBlank { "en" }
    setQualifiers("+$localeWithFallback")
  }
}

@ExperimentalRoborazziApi
fun RoborazziComposeApplierBuilder.fontScale(fontScale: Float): RoborazziComposeApplierBuilder {
  return with(FontScaleApplier(fontScale))
}

@ExperimentalRoborazziApi
data class FontScaleApplier(val fontScale: Float) :
  RoborazziComposeSetupApplier {
  override fun apply() {
    setFontScale(fontScale)
  }
}