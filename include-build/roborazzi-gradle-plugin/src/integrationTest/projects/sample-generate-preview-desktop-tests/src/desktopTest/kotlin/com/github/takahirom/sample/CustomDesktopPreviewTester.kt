package com.github.takahirom.sample

import com.github.takahirom.roborazzi.DefaultDesktopComposePreviewTester
import com.github.takahirom.roborazzi.DesktopComposePreviewTester
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

@OptIn(ExperimentalRoborazziApi::class)
class CustomDesktopPreviewTester : DesktopComposePreviewTester by DefaultDesktopComposePreviewTester() {
  override fun previews(): List<ComposablePreview<AndroidPreviewInfo>> {
    println("CustomDesktopPreviewTester previews() is called")
    return DefaultDesktopComposePreviewTester().previews()
  }
}
