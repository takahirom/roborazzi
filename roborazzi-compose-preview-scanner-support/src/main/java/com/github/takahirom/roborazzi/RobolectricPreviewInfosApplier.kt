package com.github.takahirom.roborazzi

import android.content.res.Configuration
import org.robolectric.RuntimeEnvironment.setFontScale
import org.robolectric.RuntimeEnvironment.setQualifiers
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.android.device.domain.RobolectricDeviceQualifierBuilder
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

@ExperimentalRoborazziApi
fun ComposablePreview<AndroidPreviewInfo>.applyToRobolectricConfiguration() {
  val preview = this

  fun setDevice(device: String){
    if (device.isNotBlank()) {
      RobolectricDeviceQualifierBuilder.build(device)?.run {
        setQualifiers(this)
      }
    }
  }
  setDevice(preview.previewInfo.device)

  fun setUiMode(uiMode: Int) {
    val nightMode =
      when (uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
        true -> "night"
        false -> "notnight"
      }
    setQualifiers("+$nightMode")
  }
  setUiMode(preview.previewInfo.uiMode)

  fun setLocale(locale: String) {
    val localeWithFallback = locale.ifBlank { "en" }
    setQualifiers("+$localeWithFallback")
  }
  setLocale(preview.previewInfo.locale)

  setFontScale(preview.previewInfo.fontScale)
}
