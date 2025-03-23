package com.github.takahirom.roborazzi

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import org.robolectric.RuntimeEnvironment
import org.robolectric.RuntimeEnvironment.setFontScale
import org.robolectric.RuntimeEnvironment.setQualifiers
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDisplay.getDefaultDisplay
import kotlin.math.roundToInt

@ExperimentalRoborazziApi
interface RoborazziComposeOption

@ExperimentalRoborazziApi
interface RoborazziComposeSetupOption : RoborazziComposeOption {
  class ConfigBuilder {
    private val qualifiers = mutableListOf<String>()
    private val setupsAfterQualifiers = mutableListOf<() -> Unit>()

    fun addRobolectricQualifier(qualifier: String) {
      qualifiers.add(qualifier)
    }

    fun addSetupAfterQualifiers(setup: () -> Unit) {
      setupsAfterQualifiers.add(setup)
    }

    internal fun applyToRobolectric() {
      // setQualifiers() has a little performance overhead.
      // That's why we use a single call to setQualifiers() instead of multiple calls.
      setQualifiers(qualifiers.joinToString(separator = " ") { "+$it" }.drop(1))
      setupsAfterQualifiers.forEach { it() }
    }
  }
  fun configure(configBuilder: ConfigBuilder)
}

@ExperimentalRoborazziApi
interface RoborazziComposeActivityScenarioOption : RoborazziComposeOption {
  fun configureWithActivityScenario(scenario: ActivityScenario<out Activity>)
}

@ExperimentalRoborazziApi
interface RoborazziComposeActivityScenarioCreatorOption : RoborazziComposeOption {
  @Suppress("RemoveRedundantQualifierName")
  fun createScenario(chain: () -> ActivityScenario<out androidx.activity.ComponentActivity>): ActivityScenario<out androidx.activity.ComponentActivity>
}

@ExperimentalRoborazziApi
interface RoborazziComposeComposableOption : RoborazziComposeOption {
  fun configureWithComposable(content: @Composable () -> Unit): @Composable () -> Unit
}

@ExperimentalRoborazziApi
interface RoborazziComposeCaptureOption : RoborazziComposeOption {
  fun beforeCapture()
  fun afterCapture()
}

@ExperimentalRoborazziApi
class RoborazziComposeOptions private constructor(
  private val activityScenarioCreatorOptions: List<RoborazziComposeActivityScenarioCreatorOption>,
  private val activityScenarioOptions: List<RoborazziComposeActivityScenarioOption>,
  private val composableOptions: List<RoborazziComposeComposableOption>,
  private val setupOptions: List<RoborazziComposeSetupOption>,
  private val captureOptions: List<RoborazziComposeCaptureOption>,
) {
  class Builder {
    private val activityScenarioOptions =
      mutableListOf<RoborazziComposeActivityScenarioOption>()
    private val activityScenarioCreatorOptions =
      mutableListOf<RoborazziComposeActivityScenarioCreatorOption>()
    private val composableOptions = mutableListOf<RoborazziComposeComposableOption>()
    private val setupOptions = mutableListOf<RoborazziComposeSetupOption>()
    private val captureOptions = mutableListOf<RoborazziComposeCaptureOption>()

    fun addOption(option: RoborazziComposeOption): Builder {
      if (option is RoborazziComposeActivityScenarioCreatorOption) {
        activityScenarioCreatorOptions.add(option)
      }
      if (option is RoborazziComposeActivityScenarioOption) {
        activityScenarioOptions.add(option)
      }
      if (option is RoborazziComposeComposableOption) {
        composableOptions.add(option)
      }
      if (option is RoborazziComposeSetupOption) {
        setupOptions.add(option)
      }
      if (option is RoborazziComposeCaptureOption) {
        captureOptions.add(option)
      }
      return this
    }

    fun build(): RoborazziComposeOptions {
      return RoborazziComposeOptions(
        activityScenarioCreatorOptions = activityScenarioCreatorOptions,
        activityScenarioOptions = activityScenarioOptions,
        composableOptions = composableOptions,
        setupOptions = setupOptions,
        captureOptions = captureOptions
      )
    }
  }

  fun builder(): Builder {
    return Builder()
      .apply {
        activityScenarioCreatorOptions.forEach { addOption(it) }
        activityScenarioOptions.forEach { addOption(it) }
        composableOptions.forEach { addOption(it) }
        setupOptions.forEach { addOption(it) }
      }
  }

  @ExperimentalRoborazziApi
  fun createScenario(chain: () -> ActivityScenario<out androidx.activity.ComponentActivity>): ActivityScenario<out androidx.activity.ComponentActivity> {
    return activityScenarioCreatorOptions.fold(chain) { acc, option ->
      { option.createScenario(acc) }
    }()
  }

  @ExperimentalRoborazziApi
  fun configured(
    activityScenario: ActivityScenario<out Activity>,
    content: @Composable () -> Unit
  ): @Composable () -> Unit {
    val configBuilder = RoborazziComposeSetupOption.ConfigBuilder()
    setupOptions.forEach { it.configure(configBuilder) }
    configBuilder.applyToRobolectric()
    roborazziReportLog(
      "Robolectric RuntimeEnvironment.getQualifiers() ${roboOutputName()}: ${RuntimeEnvironment.getQualifiers()}"
    )

    activityScenarioOptions.forEach { it.configureWithActivityScenario(activityScenario) }
    var appliedContent = content
    composableOptions.forEach { config ->
      appliedContent = config.configureWithComposable(appliedContent)
    }
    return {
      appliedContent()
    }
  }

  fun beforeCapture() {
    captureOptions.forEach { it.beforeCapture() }
  }

  fun afterCapture() {
    captureOptions.forEach { it.afterCapture() }
  }

  companion object {
    operator fun invoke(block: Builder.() -> Unit = {}): RoborazziComposeOptions {
      return Builder().apply(block).build()
    }
  }
}

@ExperimentalRoborazziApi
fun RoborazziComposeOptions.Builder.size(
  widthDp: Int = 0,
  heightDp: Int = 0
): RoborazziComposeOptions.Builder {
  return addOption(RoborazziComposeSizeOption(widthDp, heightDp))
}

@ExperimentalRoborazziApi
data class RoborazziComposeSizeOption(val widthDp: Int, val heightDp: Int) :
  RoborazziComposeActivityScenarioOption,
  RoborazziComposeComposableOption {
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
fun RoborazziComposeOptions.Builder.background(
  showBackground: Boolean,
  backgroundColor: Long = 0L
): RoborazziComposeOptions.Builder {
  return addOption(RoborazziComposeBackgroundOption(showBackground, backgroundColor))
}

@ExperimentalRoborazziApi
data class RoborazziComposeBackgroundOption(
  private val showBackground: Boolean,
  private val backgroundColor: Long
) : RoborazziComposeActivityScenarioOption {
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
fun RoborazziComposeOptions.Builder.uiMode(uiMode: Int): RoborazziComposeOptions.Builder {
  return addOption(RoborazziComposeUiModeOption(uiMode))
}

@ExperimentalRoborazziApi
data class RoborazziComposeUiModeOption(private val uiMode: Int) :
  RoborazziComposeSetupOption {
  override fun configure(configBuilder: RoborazziComposeSetupOption.ConfigBuilder) {
    val nightMode =
      when (uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
        true -> "night"
        false -> "notnight"
      }
    configBuilder.addRobolectricQualifier(nightMode)
  }
}

@ExperimentalRoborazziApi
fun RoborazziComposeOptions.Builder.locale(locale: String): RoborazziComposeOptions.Builder {
  return addOption(RoborazziComposeLocaleOption(locale))
}

@ExperimentalRoborazziApi
data class RoborazziComposeLocaleOption(private val locale: String) :
  RoborazziComposeSetupOption {
  override fun configure(configBuilder: RoborazziComposeSetupOption.ConfigBuilder) {
    val localeWithFallback = locale.ifBlank { "en" }
    configBuilder.addRobolectricQualifier(localeWithFallback)
  }
}

@ExperimentalRoborazziApi
fun RoborazziComposeOptions.Builder.fontScale(fontScale: Float): RoborazziComposeOptions.Builder {
  return addOption(RoborazziComposeFontScaleOption(fontScale))
}

@ExperimentalRoborazziApi
data class RoborazziComposeFontScaleOption(private val fontScale: Float) :
  RoborazziComposeSetupOption {
  init {
    require(fontScale > 0) { "fontScale must be greater than 0" }
  }

  override fun configure(configBuilder: RoborazziComposeSetupOption.ConfigBuilder) {
    configBuilder.addSetupAfterQualifiers {
      setFontScale(fontScale)
    }
  }
}

@ExperimentalRoborazziApi
fun RoborazziComposeOptions.Builder.inspectionMode(
  inspectionMode: Boolean
): RoborazziComposeOptions.Builder =
  addOption(RoborazziComposeInspectionModeOption(inspectionMode))


@ExperimentalRoborazziApi
data class RoborazziComposeInspectionModeOption(private val inspectionMode: Boolean) :
  RoborazziComposeComposableOption {
  override fun configureWithComposable(
    content: @Composable () -> Unit
  ): @Composable () -> Unit = {
    CompositionLocalProvider(LocalInspectionMode provides inspectionMode) {
      content()
    }
  }
}

/**
 * Caution: This does not work when using this with [RoborazziComposeOptions.Builder.composeTestRule].
 * Because Activity Scenario is created by the ComposeTestRule and
 * we cannot change the theme after the activity scenario is created.
 */
@ExperimentalRoborazziApi
fun RoborazziComposeOptions.Builder.activityTheme(themeResId: Int): RoborazziComposeOptions.Builder {
  return addOption(RoborazziComposeActivityThemeOption(themeResId))
}

@ExperimentalRoborazziApi
data class RoborazziComposeActivityThemeOption(private val themeResId: Int) :
  RoborazziComposeActivityScenarioCreatorOption {
  override fun createScenario(chain: () -> ActivityScenario<out ComponentActivity>): ActivityScenario<out ComponentActivity> {
    return createActivityScenario(themeResId)
  }
}
