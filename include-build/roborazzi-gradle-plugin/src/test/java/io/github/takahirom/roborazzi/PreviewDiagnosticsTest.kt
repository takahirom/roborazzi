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

  @Test
  fun errorFooterExplainsDowngradeAndDropsTheFutureErrorNotice() {
    // The ERROR footer must not claim the diagnostic "will become a build error in a future
    // release" (it already is one) and must offer the downgrade escape hatch, matching the
    // wording of junitPlatformReporting's error diagnostics.
    val failMessage = (previewDiagnosticAction(
      suppressedDiagnostics = emptySet(),
      diagnosticId = PreviewPixelCopyRenderModeDiagnosticId,
      severity = PreviewDiagnosticSeverity.ERROR,
      messageBody = body,
    ) as PreviewDiagnosticAction.Fail).message

    assertTrue("keeps the original body", failMessage.contains(body))
    assertTrue(
      "names the diagnostic id",
      failMessage.contains("Diagnostic id: $PreviewPixelCopyRenderModeDiagnosticId"),
    )
    assertTrue(
      "explains the downgrade escape hatch",
      failMessage.contains("To downgrade this error to a warning"),
    )
    assertTrue(
      "shows the suppress property",
      failMessage.contains("roborazzi.suppress=$PreviewPixelCopyRenderModeDiagnosticId"),
    )
    assertTrue(
      "does not talk about silencing a warning",
      !failMessage.contains("To silence this warning"),
    )
    assertTrue(
      "does not announce a future error-ization",
      !failMessage.contains("This will become a build error in a future release"),
    )

    // A suppressed ERROR is reported as a warning but keeps the ERROR footer wording.
    val downgraded = (previewDiagnosticAction(
      suppressedDiagnostics = setOf(PreviewPixelCopyRenderModeDiagnosticId),
      diagnosticId = PreviewPixelCopyRenderModeDiagnosticId,
      severity = PreviewDiagnosticSeverity.ERROR,
      messageBody = body,
    ) as PreviewDiagnosticAction.Warn).message
    assertTrue(
      "downgraded error keeps the downgrade wording",
      downgraded.contains("To downgrade this error to a warning"),
    )
    assertTrue(
      "downgraded error does not announce a future error-ization",
      !downgraded.contains("This will become a build error in a future release"),
    )
  }
}
