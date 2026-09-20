package com.github.takahirom.roborazzi

import android.content.res.Resources
import sergio.sastre.composable.preview.scanner.android.device.DevicePreviewInfoParser
import sergio.sastre.composable.preview.scanner.android.device.domain.RobolectricDeviceQualifierBuilder
import kotlin.math.roundToInt

/** Resolves device dimensions before scaling density; participates in the single setup update. */
@OptIn(ExperimentalRoborazziApi::class)
internal class PreviewRenderScaleOption(
  private val scale: Double,
  private val previewDevice: String = "",
  private val baseConfiguration: android.content.res.Configuration? = null,
) : RoborazziComposeSetupOption {
  init {
    require(scale.isFinite() && scale > 0.0) {
      "renderScale must be finite and greater than 0, but was $scale"
    }
  }

  override fun configure(configBuilder: RoborazziComposeSetupOption.ConfigBuilder) {
    // Resolve px-based device specs to dp FIRST. Changing their dpi before parsing
    // would change their logical size. Additive qualifiers retain dp and device metadata.
    // Blank devices retain the @Config device, just like previewDevice(). The scanner's
    // parser otherwise returns its own default device for an empty string.
    val device = previewDevice.takeIf { it.isNotBlank() }
      ?.let { DevicePreviewInfoParser.parse(it) }?.inDp()
    val resources = Resources.getSystem()
    val configuration = baseConfiguration ?: resources.configuration
    val density = device?.densityDpi ?: baseConfiguration?.densityDpi ?: resources.displayMetrics.densityDpi
    val scaledDensity = (density.toDouble() * scale).roundToInt().coerceAtLeast(1)
    // ShadowDisplayManager reconstructs dp from its already truncated pixel dimensions
    // for additive qualifiers. Supply the resolved dp explicitly to avoid losing another
    // dp on each density change (e.g. 850dp at 440dpi -> 2337px -> 849dp).
    val qualifiers = if (device != null) {
      // Device.copy() is not binary compatible across the supported ComposablePreviewScanner
      // versions (chinSize became a ChinSize after 0.7.0), so rewrite the dpi qualifier the
      // builder already emits instead of rebuilding the Device.
      RobolectricDeviceQualifierBuilder.build(device).withDensityDpi(scaledDensity)
    } else {
      "w${configuration.screenWidthDp}dp-h${configuration.screenHeightDp}dp-${scaledDensity}dpi"
    }
    configBuilder.addRobolectricQualifier(qualifiers)
    RenderScaleVerification.markApplied()
  }
}

private val densityQualifierRegex = Regex("""(^|-)\d+dpi(?=-|$)""")

/** Replaces the dpi qualifier in a Robolectric qualifier string, appending one if absent. */
private fun String.withDensityDpi(dpi: Int): String =
  if (densityQualifierRegex.containsMatchIn(this)) {
    replace(densityQualifierRegex) { "${it.groupValues[1]}${dpi}dpi" }
  } else {
    "$this-${dpi}dpi"
  }
