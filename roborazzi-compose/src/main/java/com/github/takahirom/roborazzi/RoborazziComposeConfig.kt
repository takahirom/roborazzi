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
interface RoborazziComposeConfig

@ExperimentalRoborazziApi
interface RoborazziComposeSetupConfig : RoborazziComposeConfig {
  fun configure()
}

@ExperimentalRoborazziApi
interface RoborazziComposeActivityScenarioConfig : RoborazziComposeConfig {
  fun configureWithActivityScenario(scenario: ActivityScenario<out Activity>)
}

@ExperimentalRoborazziApi
interface RoborazziComposeComposableConfig : RoborazziComposeConfig {
  fun configureWithComposable(content: @Composable () -> Unit): @Composable () -> Unit
}

@ExperimentalRoborazziApi
class RoborazziComposeConfigBuilder {
  private val activityScenarioConfigs =
    mutableListOf<RoborazziComposeActivityScenarioConfig>()
  private val composableConfigs = mutableListOf<RoborazziComposeComposableConfig>()
  private val setupConfigs = mutableListOf<RoborazziComposeSetupConfig>()

  fun with(config: RoborazziComposeConfig): RoborazziComposeConfigBuilder {
    if (config is RoborazziComposeActivityScenarioConfig) {
      activityScenarioConfigs.add(config)
    }
    if (config is RoborazziComposeComposableConfig) {
      composableConfigs.add(config)
    }
    if (config is RoborazziComposeSetupConfig) {
      setupConfigs.add(config)
    }
    return this
  }

  @ExperimentalRoborazziApi
  fun configure(
    scenario: ActivityScenario<out Activity>,
    content: @Composable () -> Unit
  ): @Composable () -> Unit {
    setupConfigs.forEach { it.configure() }
    activityScenarioConfigs.forEach { it.configureWithActivityScenario(scenario) }
    var appliedContent = content
    composableConfigs.forEach { config ->
      appliedContent = config.configureWithComposable(appliedContent)
    }
    return {
      appliedContent()
    }
  }
}

@ExperimentalRoborazziApi
fun RoborazziComposeConfigBuilder.size(
  widthDp: Int = 0,
  heightDp: Int = 0
): RoborazziComposeConfigBuilder {
  return with(RoborazziComposeSizeConfig(widthDp, heightDp))
}

@ExperimentalRoborazziApi
data class RoborazziComposeSizeConfig(val widthDp: Int, val heightDp: Int) :
  RoborazziComposeActivityScenarioConfig,
  RoborazziComposeComposableConfig {
  override fun configureWithActivityScenario(scenario: ActivityScenario<out Activity>) {
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

  override fun configureWithComposable(content: @Composable () -> Unit): @Composable () -> Unit {
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
fun RoborazziComposeConfigBuilder.background(
  showBackground: Boolean,
  backgroundColor: Long = 0L
): RoborazziComposeConfigBuilder {
  return with(RoborazziComposeBackgroundConfig(showBackground, backgroundColor))
}

@ExperimentalRoborazziApi
data class RoborazziComposeBackgroundConfig(
  private val showBackground: Boolean,
  private val backgroundColor: Long
) : RoborazziComposeActivityScenarioConfig {
  override fun configureWithActivityScenario(scenario: ActivityScenario<out Activity>) {
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
fun RoborazziComposeConfigBuilder.uiMode(uiMode: Int): RoborazziComposeConfigBuilder {
  return with(RoborazziComposeUiModeConfig(uiMode))
}

@ExperimentalRoborazziApi
data class RoborazziComposeUiModeConfig(private val uiMode: Int) :
  RoborazziComposeSetupConfig {
  override fun configure() {
    val nightMode =
      when (uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
        true -> "night"
        false -> "notnight"
      }
    setQualifiers("+$nightMode")
  }
}

@ExperimentalRoborazziApi
fun RoborazziComposeConfigBuilder.locale(locale: String): RoborazziComposeConfigBuilder {
  return with(RoborazziComposeLocaleConfig(locale))
}

@ExperimentalRoborazziApi
data class RoborazziComposeLocaleConfig(private val locale: String) :
  RoborazziComposeSetupConfig {
  override fun configure() {
    val localeWithFallback = locale.ifBlank { "en" }
    setQualifiers("+$localeWithFallback")
  }
}

@ExperimentalRoborazziApi
fun RoborazziComposeConfigBuilder.fontScale(fontScale: Float): RoborazziComposeConfigBuilder {
  return with(RoborazziComposeFontScaleConfig(fontScale))
}

@ExperimentalRoborazziApi
data class RoborazziComposeFontScaleConfig(private val fontScale: Float) :
  RoborazziComposeSetupConfig {
  init {
    require(fontScale > 0) { "fontScale must be greater than 0" }
  }
  override fun configure() {
    setFontScale(fontScale)
  }
}