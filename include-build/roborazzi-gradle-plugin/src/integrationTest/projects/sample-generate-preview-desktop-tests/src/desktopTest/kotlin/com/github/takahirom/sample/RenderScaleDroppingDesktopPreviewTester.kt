package com.github.takahirom.sample

import com.github.takahirom.roborazzi.DefaultDesktopComposePreviewTester
import com.github.takahirom.roborazzi.DesktopComposePreviewTester
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi

/**
 * Builds its options from scratch instead of from the plugin's, keeping only the scan options.
 *
 * This is the mistake `DesktopRenderScaleVerification` exists to catch: everything still runs, but
 * the previews are recorded at the unscaled density, because
 * `DesktopComposePreviewTester.Options()` resets `renderScale` to 1.0.
 */
@OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
private val droppedOptions: DesktopComposePreviewTester.Options
  get() = DesktopComposePreviewTester.Options(
    scanOptions = DesktopComposePreviewTester.defaultOptionsFromPlugin.scanOptions,
  )

@OptIn(ExperimentalRoborazziApi::class)
class RenderScaleDroppingDesktopPreviewTester :
  DesktopComposePreviewTester by DefaultDesktopComposePreviewTester(options = droppedOptions) {
  override fun options(): DesktopComposePreviewTester.Options = droppedOptions
}
