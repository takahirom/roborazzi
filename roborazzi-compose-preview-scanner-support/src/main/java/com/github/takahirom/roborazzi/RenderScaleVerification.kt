package com.github.takahirom.roborazzi

/**
 * Detects a [ComposePreviewTester] that drops the `renderScale` configured in the Gradle
 * extension before the preview is captured.
 *
 * The plugin can pass the configured value to the tester, but only the capture can apply it.
 * A custom tester that replaces `options()` or `test()` can therefore record screenshots at the
 * unscaled density without anything failing. [PreviewRenderScaleOption] records the scale it
 * applied, and the generated test compares the records with the configured value once the
 * capture has run.
 */
@InternalRoborazziApi
object RenderScaleVerification {
  private val appliedScales = mutableListOf<Double>()
  private var expectedScale: Double? = null

  /** Records the scale this preview should be captured at, including a per-preview override. */
  @InternalRoborazziApi
  fun expect(scale: Double) {
    expectedScale = scale
  }

  internal fun markApplied(scale: Double) {
    appliedScales += scale
  }

  /** Clears the recorded scales before a preview is captured. Called by the generated test. */
  @InternalRoborazziApi
  fun beforeTest() {
    appliedScales.clear()
    expectedScale = null
  }

  /**
   * Fails when the configured scale did not reach every capture the test ran.
   *
   * A test may capture more than once, so each capture has to use the configured value: one
   * correct capture must not hide another that used a different scale or none at all.
   *
   * Does nothing when no scale is configured or when Roborazzi is not recording or verifying,
   * because no capture runs in that case.
   */
  @OptIn(ExperimentalRoborazziApi::class)
  @InternalRoborazziApi
  fun afterTest(tester: ComposePreviewTester<*>) {
    val configuredScale =
      expectedScale ?: ComposePreviewTester.defaultOptionsFromPlugin.renderScale
    if (configuredScale == 1.0) return
    val applied = appliedScales.toList()
    if (applied.isNotEmpty() && applied.all { it == configuredScale }) return
    if (!provideRoborazziContext().options.taskType.isEnabled()) return
    val appliedDescription = when {
      applied.isEmpty() -> "did not apply it, so this screenshot was captured at the unscaled density"
      else -> "applied ${applied.joinToString()} instead"
    }
    throw IllegalStateException(
      "renderScale = $configuredScale is configured in generateComposePreviewRobolectricTests, " +
        "but ${tester::class.java.name} $appliedDescription.\n" +
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
