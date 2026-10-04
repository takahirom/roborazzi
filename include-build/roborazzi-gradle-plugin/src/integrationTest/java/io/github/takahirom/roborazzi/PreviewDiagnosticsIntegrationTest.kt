package io.github.takahirom.roborazzi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Gradle integration coverage for the Compose preview generation diagnostics
 * (preview.includeAndroidResources / preview.pixelCopyRenderMode). The pure decision logic is
 * unit-tested in PreviewDiagnosticsTest; this exercises the real plugin wiring: that the
 * warnings fire (with their stable id and suppression footer) when the feature is enabled and
 * misconfigured, that roborazzi.suppress silences them, and — the regression guard for the
 * enable-gating fix — that nothing fires when the feature is disabled or correctly configured.
 *
 * The diagnostics are emitted at configuration time (from afterEvaluate and the test task's
 * configureEach), so each scenario configures the project and realizes the recordRoborazziDebug
 * task graph with `--dry-run`; no tests actually run, which keeps these fast and lets a
 * deliberately misconfigured project be exercised without needing its tests to pass.
 *
 * Run with:
 * ./gradlew roborazzi-gradle-plugin:integrationTest --tests "*PreviewDiagnostics*"
 *
 * Note: integrationTest runs one process at a time.
 */
class PreviewDiagnosticsIntegrationTest {

  private companion object {
    // Stable, namespaced diagnostic ids. Kept in sync with the internal constants in
    // AndroidGeneratePreviewTestsDiagnostics.kt (this source set cannot see internal symbols).
    const val INCLUDE_ANDROID_RESOURCES_ID = "preview.includeAndroidResources"
    const val PIXEL_COPY_RENDER_MODE_ID = "preview.pixelCopyRenderMode"

    // Footer markers shared by every WARNING-severity preview diagnostic.
    const val FUTURE_ERROR_NOTICE = "This will become a build error in a future release. Suppress it if intentional."
    const val SUPPRESS_PROPERTY = "roborazzi.suppress"
  }

  @get:Rule
  val testProjectDir = TemporaryFolder()

  /**
   * (1) Feature enabled but misconfigured: both isIncludeAndroidResources and
   * pixelCopyRenderMode are wrong, so both advisory warnings fire, each carrying its stable
   * diagnostic id and the WARNING suppression footer.
   */
  @Test
  fun warnsWithIdAndFooterWhenEnabledAndMisconfigured() {
    RoborazziGradleRootProject(testProjectDir).previewModule.apply {
      buildGradle.isIncludeAndroidResources = false
      buildGradle.setPixelCopyRenderModeHardware = false

      val output = configureAndReportDiagnostics().output

      assertTrue(
        "Expected the includeAndroidResources warning with its diagnostic id.\n$output",
        output.contains("Diagnostic id: $INCLUDE_ANDROID_RESOURCES_ID"),
      )
      assertTrue(
        "Expected the pixelCopyRenderMode warning with its diagnostic id.\n$output",
        output.contains("Diagnostic id: $PIXEL_COPY_RENDER_MODE_ID"),
      )
      // Each diagnostic's own footer must carry the forthcoming-error notice (the notice line
      // immediately precedes the "Diagnostic id:" line). A plain contains() would pass if only
      // one of the two warnings kept its footer. The pixelCopyRenderMode warning fires once per
      // Test task variant, so total occurrence counts are not stable — anchor per id instead.
      for (id in listOf(INCLUDE_ANDROID_RESOURCES_ID, PIXEL_COPY_RENDER_MODE_ID)) {
        assertTrue(
          "Expected the forthcoming-error notice in the footer of $id.\n$output",
          Regex(Regex.escape(FUTURE_ERROR_NOTICE) + """\s*Diagnostic id: """ + Regex.escape(id))
            .containsMatchIn(output),
        )
      }
      assertTrue(
        "Expected the footer to show how to suppress the includeAndroidResources warning.\n$output",
        output.contains("$SUPPRESS_PROPERTY=$INCLUDE_ANDROID_RESOURCES_ID"),
      )
      assertTrue(
        "Expected the footer to show how to suppress the pixelCopyRenderMode warning.\n$output",
        output.contains("$SUPPRESS_PROPERTY=$PIXEL_COPY_RENDER_MODE_ID"),
      )
    }
  }

  /**
   * (2) Suppressing an id silences that warning. Only pixelCopyRenderMode is left
   * misconfigured-free (set correctly) so the sole warning candidate is
   * includeAndroidResources, and suppressing its id must remove it from the output.
   */
  @Test
  fun suppressingIdSilencesTheWarning() {
    RoborazziGradleRootProject(testProjectDir).previewModule.apply {
      buildGradle.isIncludeAndroidResources = false
      buildGradle.setPixelCopyRenderModeHardware = true

      val output =
        configureAndReportDiagnostics("-Proborazzi.suppress=$INCLUDE_ANDROID_RESOURCES_ID").output

      assertFalse(
        "Expected the includeAndroidResources warning to be silenced by roborazzi.suppress.\n$output",
        output.contains("Diagnostic id: $INCLUDE_ANDROID_RESOURCES_ID"),
      )
    }
  }

  /**
   * (3) Regression guard for the enable-gating fix: with preview generation disabled, no
   * preview diagnostic fires even though both settings are misconfigured. Before the fix the
   * KMP path warned regardless of enable; this asserts the Android path never does.
   */
  @Test
  fun doesNotWarnWhenPreviewGenerationDisabled() {
    RoborazziGradleRootProject(testProjectDir).previewModule.apply {
      buildGradle.enable = false
      buildGradle.isIncludeAndroidResources = false
      buildGradle.setPixelCopyRenderModeHardware = false

      val output = configureAndReportDiagnostics().output

      assertFalse(
        "Expected no preview diagnostic when preview generation is disabled.\n$output",
        output.contains("Diagnostic id: preview."),
      )
    }
  }

  /**
   * (4) False-positive guard: a correctly configured project (both settings right) trips no
   * preview diagnostic.
   */
  @Test
  fun doesNotWarnWhenCorrectlyConfigured() {
    RoborazziGradleRootProject(testProjectDir).previewModule.apply {
      buildGradle.isIncludeAndroidResources = true
      buildGradle.setPixelCopyRenderModeHardware = true

      val output = configureAndReportDiagnostics().output

      assertFalse(
        "Expected no preview diagnostic when the project is correctly configured.\n$output",
        output.contains("Diagnostic id: preview."),
      )
    }
  }
}
