package com.github.takahirom.sample

import com.github.takahirom.roborazzi.*
import com.github.takahirom.roborazzi.ComposePreviewTester.TestParameter.JUnit4TestParameter.AndroidPreviewJUnit4TestParameter

/**
 * Builds a new Options instead of using super.options().copy(...), which resets the renderScale
 * configured in Gradle to 1.0. The generated test is expected to report this.
 */
public class RenderScaleDroppingPreviewTester :
  ComposePreviewTester<AndroidPreviewJUnit4TestParameter> by AndroidComposePreviewTester() {
  override fun options(): ComposePreviewTester.Options = ComposePreviewTester.Options(
    scanOptions = super.options().scanOptions,
  )
}
