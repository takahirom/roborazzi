package io.github.takahirom.roborazzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit coverage for the preview-generation diagnostic decision logic
 * ([previewDiagnosticAction] / [previewDiagnosticMessage]).
 *
 * These are pure functions (no Gradle API), so the tests run on the unit-test classpath
 * without gradle-api and cover the three behaviors the feature promises: a fired warning
 * carries the id and suppression footer, roborazzi.suppress silences it, and an unrelated
 * suppression id leaves it firing. The ERROR cases document the Phase-2 severity structure.
 */
class PreviewDiagnosticsTest {
  private val body = "Roborazzi: Please set 'isIncludeAndroidResources = true' ..."

  @Test
  fun warningFiresWithIdAndSuppressionFooter() {
    val action = previewDiagnosticAction(
      suppressedDiagnostics = emptySet(),
      diagnosticId = PreviewIncludeAndroidResourcesDiagnosticId,
      severity = PreviewDiagnosticSeverity.WARNING,
      messageBody = body,
    )

    assertTrue("must fire as a warning", action is PreviewDiagnosticAction.Warn)
    val message = (action as PreviewDiagnosticAction.Warn).message
    assertTrue("keeps the original body", message.contains(body))
    assertTrue(
      "names the diagnostic id",
      message.contains("Diagnostic id: $PreviewIncludeAndroidResourcesDiagnosticId"),
    )
    assertTrue(
      "shows how to suppress",
      message.contains("roborazzi.suppress=$PreviewIncludeAndroidResourcesDiagnosticId"),
    )
    assertTrue(
      "announces the future error-ization",
      message.contains("This will become a build error in a future release. Suppress it if intentional."),
    )
  }

  @Test
  fun suppressingTheIdSilencesTheWarning() {
    val action = previewDiagnosticAction(
      suppressedDiagnostics = setOf(PreviewPixelCopyRenderModeDiagnosticId),
      diagnosticId = PreviewPixelCopyRenderModeDiagnosticId,
      severity = PreviewDiagnosticSeverity.WARNING,
      messageBody = body,
    )

    assertEquals(PreviewDiagnosticAction.Silence, action)
  }

  @Test
  fun suppressingAnUnrelatedIdLeavesTheWarningFiring() {
    val action = previewDiagnosticAction(
      suppressedDiagnostics = setOf(PreviewIncludeAndroidResourcesDiagnosticId),
      diagnosticId = PreviewPixelCopyRenderModeDiagnosticId,
      severity = PreviewDiagnosticSeverity.WARNING,
      messageBody = body,
    )

    assertTrue("unrelated suppression must not silence it", action is PreviewDiagnosticAction.Warn)
  }

  @Test
  fun errorSeverityFailsWhenNotSuppressedAndDowngradesWhenSuppressed() {
    // Documents the Phase-2 structure: flipping a diagnostic's severity to ERROR is the only
    // change needed for it to fail the build, and listing its id downgrades it to a warning.
    val notSuppressed = previewDiagnosticAction(
      suppressedDiagnostics = emptySet(),
      diagnosticId = PreviewPixelCopyRenderModeDiagnosticId,
      severity = PreviewDiagnosticSeverity.ERROR,
      messageBody = body,
    )
    assertTrue("an unsuppressed error must fail the build", notSuppressed is PreviewDiagnosticAction.Fail)

    val suppressed = previewDiagnosticAction(
      suppressedDiagnostics = setOf(PreviewPixelCopyRenderModeDiagnosticId),
      diagnosticId = PreviewPixelCopyRenderModeDiagnosticId,
      severity = PreviewDiagnosticSeverity.ERROR,
      messageBody = body,
    )
    assertTrue("a suppressed error downgrades to a warning", suppressed is PreviewDiagnosticAction.Warn)
  }
}
