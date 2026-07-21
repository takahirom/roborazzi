package io.github.takahirom.roborazzi

// Advisory diagnostics for the Compose preview test generation feature. They share the
// Roborazzi-wide suppression mechanism ([RoborazziDiagnosticSuppression], defined alongside
// the JUnit Platform reporting diagnostics) but keep their own message/severity plumbing here
// so the two features stay independent.
//
// Everything in THIS file is pure (no Gradle API reference anywhere in the facade) so a unit
// test can exercise the decision logic on a classpath without gradle-api. The Gradle-facing
// side effect (log vs. throw) lives in reportPreviewDiagnostic in the configurator file, which
// already depends on the Gradle API — keeping it out of here is what lets the tests load this
// facade class at all.

// Stable, namespaced ids: both the grep anchor in each message and the token a user lists in
// roborazzi.suppress. Keep these strings stable.
internal const val PreviewIncludeAndroidResourcesDiagnosticId = "preview.includeAndroidResources"
internal const val PreviewPixelCopyRenderModeDiagnosticId = "preview.pixelCopyRenderMode"

/**
 * Severity of a preview-generation diagnostic. Today every preview diagnostic is a
 * [WARNING]. Flipping one to [ERROR] (the planned Phase 2, where the advisory becomes a hard
 * build failure) is a single-line change at the diagnostic's call site — mirroring the
 * error/warn split of junitPlatformReporting.doubleExecution.
 */
internal enum class PreviewDiagnosticSeverity { WARNING, ERROR }

/**
 * What a diagnostic resolves to once suppression and severity are applied. Kept separate from
 * the Gradle-facing side effect (log vs. throw) so the decision is a pure function and can be
 * unit-tested without the Gradle API.
 */
internal sealed interface PreviewDiagnosticAction {
  /** Suppressed warning: emit nothing. */
  object Silence : PreviewDiagnosticAction

  /** Emit [message] at warning level. */
  data class Warn(val message: String) : PreviewDiagnosticAction

  /** Fail the build with [message]. */
  data class Fail(val message: String) : PreviewDiagnosticAction
}

// The tail appended to every preview diagnostic message: the diagnostic id and how to change
// its behavior. Severity-dependent so it stays truthful once a diagnostic is promoted from
// WARNING to ERROR (a single-line change at the call site):
//  - WARNING keeps the original advisory wording: it announces the forthcoming error-ization
//    and explains that listing the id silences the warning.
//  - ERROR drops the "will become a build error" line (it already is one) and, matching
//    junitPlatformReporting's suppressionFooter, explains that listing the id downgrades the
//    error to a warning rather than silencing it.
// Stable, greppable wording. Returned as a standalone trimMargin'd block that callers
// concatenate onto the (independently formatted) message body.
private fun previewSuppressionFooter(
  diagnosticId: String,
  severity: PreviewDiagnosticSeverity,
): String = when (severity) {
  PreviewDiagnosticSeverity.WARNING ->
    """
      |  This will become a build error in a future release. Suppress it if intentional.
      |  Diagnostic id: $diagnosticId
      |  To silence this warning, add to gradle.properties (comma-separate multiple ids):
      |    ${RoborazziDiagnosticSuppression.PROPERTY}=$diagnosticId
    """.trimMargin()

  PreviewDiagnosticSeverity.ERROR ->
    """
      |  Diagnostic id: $diagnosticId
      |  To downgrade this error to a warning, add to gradle.properties (comma-separate multiple ids):
      |    ${RoborazziDiagnosticSuppression.PROPERTY}=$diagnosticId
    """.trimMargin()
}

/**
 * Builds the full diagnostic text: the human-readable [messageBody] (kept from the original
 * advisory warnings) followed by the [severity]-appropriate suppression footer.
 */
internal fun previewDiagnosticMessage(
  diagnosticId: String,
  severity: PreviewDiagnosticSeverity,
  messageBody: String,
): String =
  messageBody.trimEnd() + "\n" + previewSuppressionFooter(diagnosticId, severity)

/**
 * Resolves a preview diagnostic to an action, honoring roborazzi.suppress:
 *  - not suppressed, [PreviewDiagnosticSeverity.WARNING] -> [PreviewDiagnosticAction.Warn]
 *  - not suppressed, [PreviewDiagnosticSeverity.ERROR]   -> [PreviewDiagnosticAction.Fail]
 *  - suppressed, WARNING -> [PreviewDiagnosticAction.Silence] (silenced outright)
 *  - suppressed, ERROR   -> [PreviewDiagnosticAction.Warn] (downgraded to a warning)
 *
 * The suppressed-error downgrade matches junitPlatformReporting's contract, so once a
 * diagnostic is promoted to ERROR, listing its id keeps the current warning behavior.
 */
internal fun previewDiagnosticAction(
  suppressedDiagnostics: Set<String>,
  diagnosticId: String,
  severity: PreviewDiagnosticSeverity,
  messageBody: String,
): PreviewDiagnosticAction {
  val suppressed =
    RoborazziDiagnosticSuppression.isSuppressed(suppressedDiagnostics, diagnosticId)
  // The message carries the severity-appropriate footer. A suppressed ERROR is reported as a
  // Warn but keeps the ERROR footer ("To downgrade this error to a warning ..."), matching
  // junitPlatformReporting: it is a downgraded error, not an ordinary advisory warning.
  val message = previewDiagnosticMessage(diagnosticId, severity, messageBody)
  return when (severity) {
    PreviewDiagnosticSeverity.WARNING ->
      if (suppressed) PreviewDiagnosticAction.Silence else PreviewDiagnosticAction.Warn(message)

    PreviewDiagnosticSeverity.ERROR ->
      if (suppressed) PreviewDiagnosticAction.Warn(message) else PreviewDiagnosticAction.Fail(message)
  }
}
