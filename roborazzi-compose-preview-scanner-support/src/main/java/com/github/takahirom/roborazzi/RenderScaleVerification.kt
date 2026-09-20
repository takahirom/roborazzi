package com.github.takahirom.roborazzi

/**
 * Detects a [ComposePreviewTester] that drops the `renderScale` configured in the Gradle
 * extension before the preview is captured.
 *
 * The plugin can pass the configured value to the tester, but only the capture can apply it.
 * A custom tester that replaces `options()` or `test()` can therefore record screenshots at the
 * unscaled density without anything failing. [PreviewRenderScaleOption] marks the scale as
 * applied, and the generated test checks the mark once the capture has run.
 */
@InternalRoborazziApi
object RenderScaleVerification {
  private var applied = false

  internal fun markApplied() {
    applied = true
  }

  /** Clears the mark before a preview is captured. Called by the generated test. */
  @InternalRoborazziApi
  fun beforeTest() {
    applied = false
  }

  /**
   * Fails when the plugin configured a scale that never reached the capture.
   *
   * Does nothing when no scale is configured, when the scale was applied, or when Roborazzi is
   * not recording or verifying, because no capture runs in that case.
   */
  @OptIn(ExperimentalRoborazziApi::class)
  @InternalRoborazziApi
  fun afterTest(tester: ComposePreviewTester<*>) {
    val configuredScale = ComposePreviewTester.defaultOptionsFromPlugin.renderScale
    if (configuredScale == 1.0 || applied) return
    if (!provideRoborazziContext().options.taskType.isEnabled()) return
    throw IllegalStateException(
      "renderScale = $configuredScale is configured in generateComposePreviewRobolectricTests, " +
        "but ${tester::class.java.name} did not apply it, so this screenshot was captured at the " +
        "unscaled density.\n" +
        "\n" +
        "Carry the configured value through to the capture:\n" +
        "  - If you override options(), build it with super.options().copy(...) rather than " +
        "constructing a new ComposePreviewTester.Options, which resets renderScale to 1.0.\n" +
        "  - If you override test(), pass options().renderScale to " +
        "preview.toRoborazziComposeOptions(renderScale).\n" +
        "\n" +
        "Alternatively, remove renderScale from the Gradle configuration.\n" +
        "options().renderScale returned ${tester.options().renderScale}."
    )
  }
}
