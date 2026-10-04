package io.github.takahirom.roborazzi

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger

/**
 * Emits a preview diagnostic through [logger], failing the build with a [GradleException] when
 * the resolved action is [PreviewDiagnosticAction.Fail]. Thin wrapper over the pure
 * [previewDiagnosticAction].
 *
 * This lives in its own file on purpose: the JVM verifier eagerly loads the thrown
 * [GradleException] type when it links any facade class containing this `throw`. Keeping it
 * apart from the pure decision helpers (AndroidGeneratePreviewTestsDiagnostics.kt) and the
 * unit-tested version helpers (AndroidGeneratePreviewTestsConfigurator.kt) is what lets those
 * facades load on a unit-test classpath that has no gradle-api. Nothing here is unit-tested.
 */
internal fun reportPreviewDiagnostic(
  logger: Logger,
  suppressedDiagnostics: Set<String>,
  diagnosticId: String,
  severity: PreviewDiagnosticSeverity,
  messageBody: String,
) {
  when (val action =
    previewDiagnosticAction(suppressedDiagnostics, diagnosticId, severity, messageBody)) {
    PreviewDiagnosticAction.Silence -> {}
    is PreviewDiagnosticAction.Warn -> logger.warn(action.message)
    is PreviewDiagnosticAction.Fail -> throw GradleException(action.message)
  }
}
