package com.github.takahirom.roborazzi

/**
 * Detects a [DesktopComposePreviewTester] that drops the `renderScale` configured in the Gradle
 * extension before the preview is captured.
 *
 * The plugin can pass the configured value to the tester, but only the capture can apply it. A
 * custom tester that replaces `options()` or sizes its own surface can therefore record
 * screenshots at the unscaled density without anything failing. The default tester records the
 * scale it resolved each preview with, and the generated test compares the records with the
 * configured value once the capture has run.
 *
 * This is the Compose Desktop counterpart of `RenderScaleVerification`, kept separate because the
 * two runtimes live in different modules and their testers share no supertype.
 */
@InternalRoborazziApi
object DesktopRenderScaleVerification {
  private val appliedScales = mutableListOf<Double>()

  @ExperimentalRoborazziApi
  internal fun markApplied(scale: Double) {
    synchronized(appliedScales) { appliedScales += scale }
  }

  /**
   * Clears the recorded scales.
   *
   * Called once per test method by the parameterized generated test, and once per class by
   * [DesktopPreviewSceneReuseRunner] - with scene reuse on, a whole group is resolved before its
   * first capture runs, so clearing per capture would throw away the records of the previews
   * behind it in the group.
   */
  @InternalRoborazziApi
  fun beforeTest() {
    synchronized(appliedScales) { appliedScales.clear() }
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
  fun afterTest(tester: DesktopComposePreviewTester) {
    val configuredScale = DesktopComposePreviewTester.defaultOptionsFromPlugin.renderScale
    if (configuredScale == 1.0) return
    val applied = synchronized(appliedScales) { appliedScales.toList() }
    if (applied.isNotEmpty() && applied.all { it == configuredScale }) return
    if (!provideRoborazziContext().options.taskType.isEnabled()) return
    val appliedDescription = when {
      applied.isEmpty() ->
        "did not apply it, so this screenshot was captured at the unscaled density"
      else -> "applied ${applied.joinToString()} instead"
    }
    throw IllegalStateException(
      "renderScale = $configuredScale is configured in generateComposePreviewDesktopTests, " +
        "but ${tester::class.java.name} $appliedDescription.\n" +
        "\n" +
        "Carry the configured value through to the capture:\n" +
        "  - If you override options(), build it with super.options().copy(...) rather than " +
        "constructing a new DesktopComposePreviewTester.Options, which resets renderScale to " +
        "1.0.\n" +
        "  - If you size the surface yourself, pass options().renderScale to " +
        "DesktopPreviewRenderSpec.resolve(previewInfo, renderProfile, renderScale).\n" +
        "\n" +
        "Alternatively, remove renderScale from the Gradle configuration.\n" +
        "options().renderScale returned ${tester.options().renderScale}."
    )
  }
}
