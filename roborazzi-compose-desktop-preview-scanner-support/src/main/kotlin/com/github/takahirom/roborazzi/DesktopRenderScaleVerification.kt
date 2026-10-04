package com.github.takahirom.roborazzi

/**
 * Detects a [DesktopComposePreviewTester] that drops the `renderScale` configured in the Gradle
 * extension before the preview is captured.
 *
 * The plugin passes the configured value to the tester through `options()`, but only the capture
 * can apply it. A custom tester that builds its own `Options` resets `renderScale` to 1.0 and would
 * record screenshots at the unscaled density without anything failing, so the generated test
 * compares the tester's `options().renderScale` with the configured value before capturing.
 *
 * It holds no state: it reads the configured value and the tester's options each time, so nothing
 * has to be cleared between tests.
 *
 * This is the Compose Desktop counterpart of `RenderScaleVerification`, kept separate because the
 * two runtimes live in different modules and their testers share no supertype.
 */
@InternalRoborazziApi
object DesktopRenderScaleVerification {
  /**
   * Reports [RoborazziProblems.ComposePreviewRenderScaleMismatch], which fails by default, when
   * [tester]'s `options().renderScale` is not the configured scale.
   *
   * Does nothing when no scale is configured, so a tester may still pick its own scale, or when
   * Roborazzi is not recording or verifying, because no capture runs in that case.
   */
  @OptIn(ExperimentalRoborazziApi::class)
  @InternalRoborazziApi
  fun verify(tester: DesktopComposePreviewTester) {
    val configuredScale = DesktopComposePreviewTester.defaultOptionsFromPlugin.renderScale
    if (configuredScale == 1.0) return
    val testerScale = tester.options().renderScale
    if (testerScale == configuredScale) return
    if (!provideRoborazziContext().options.taskType.isEnabled()) return
    RoborazziProblems.report(
      RoborazziProblems.ComposePreviewRenderScaleMismatch,
      "renderScale = $configuredScale is configured in generateComposePreviewDesktopTests, " +
        "but ${tester::class.java.name}.options().renderScale returned $testerScale.\n" +
        "\n" +
        "Carry the configured value through to the capture:\n" +
        "  - If you override options(), build it with super.options().copy(...) rather than " +
        "constructing a new DesktopComposePreviewTester.Options, which resets renderScale to " +
        "1.0.\n" +
        "  - If you size the surface yourself, pass options().renderScale to " +
        "DesktopPreviewRenderSpec.resolve(previewInfo, deviceProfile, renderScale).\n" +
        "\n" +
        "Alternatively, remove renderScale from the Gradle configuration."
    )
  }
}
