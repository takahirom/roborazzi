package io.github.takahirom.roborazzi

import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import org.gradle.util.GradleVersion

// These declarations live in their own file (not in RoborazziPlugin.kt) on purpose: a
// top-level `val` initialized from the Gradle API (GradleTestReportAttachmentsMinVersion)
// would run in RoborazziPluginKt's static initializer, so loading RoborazziPluginKt for any
// of its other top-level functions (e.g. from a unit test on a classpath without gradle-api)
// would fail with NoClassDefFoundError. Keeping the Gradle-API-touching diagnostics here
// isolates that initializer to a class only loaded when the diagnostics actually run.

// Stable, greppable prefix so an AI coding agent can find and act on these warnings.
private const val JUnitPlatformReportingLogPrefix = "Roborazzi JUnit Platform reporting:"

// A jar carrying this in its file name marks the reporting module on a test runtime
// classpath. Matches both the published artifact and the included-build project jar.
private const val JUnitPlatformReportingArtifactName = "roborazzi-junit-platform-reporting"

private const val JUnitPlatformReportingDocsUrl =
  "https://takahirom.github.io/roborazzi/junit-platform-reporting.html"

// The stock JUnit Vintage engine id. Leaving it enabled alongside roborazzi-vintage
// makes every JUnit4 test run twice.
private const val JUnitVintageEngineId = "junit-vintage"

// The engine this module registers to wrap and run JUnit4 tests itself.
private const val RoborazziVintageEngineId = "roborazzi-vintage"

/**
 * The Roborazzi-wide diagnostic suppression mechanism, keyed off the [PROPERTY] Gradle
 * property. Only the JUnit Platform reporting diagnostics use it today, but parsing and
 * id-matching live here (not inline in one feature) so future Roborazzi diagnostics can
 * share the same property and behavior.
 *
 * The property lists diagnostic ids to suppress, comma-separated. Ids are namespaced by
 * feature, e.g. `junitPlatformReporting.doubleExecution`. Suppressing a warning silences it;
 * suppressing an error is up to each diagnostic (JUnit Platform reporting downgrades it to a
 * warning). Callers resolve the id set once (a Gradle property is a Configuration Cache
 * input) and capture only that Set<String>, so no Project reference leaks into task actions.
 */
internal object RoborazziDiagnosticSuppression {
  const val PROPERTY = "roborazzi.suppress"

  fun parse(roborazziProperties: Map<String, Any?>): Set<String> =
    (roborazziProperties[PROPERTY] as? String)
      ?.split(",")
      ?.map { it.trim() }
      ?.filter { it.isNotEmpty() }
      ?.toSet()
      ?: emptySet()

  fun isSuppressed(suppressedIds: Set<String>, diagnosticId: String): Boolean =
    diagnosticId in suppressedIds
}

// Stable, namespaced ids for the diagnostics, used both as the grep anchor in each message
// and as the token a user lists in roborazzi.suppress. Keep these strings stable.
private const val OldGradleDiagnosticId = "junitPlatformReporting.oldGradle"
private const val NotJUnitPlatformDiagnosticId = "junitPlatformReporting.notJUnitPlatform"
private const val DoubleExecutionDiagnosticId = "junitPlatformReporting.doubleExecution"
private const val EngineNotSelectedDiagnosticId = "junitPlatformReporting.engineNotSelected"

// The tail appended to every diagnostic message: its id and how to suppress it. The error
// (downgradable = true) explains that suppressing downgrades it to a warning; warnings say
// suppressing silences them. Returned as a standalone block that callers concatenate onto
// the already-trimMargin'd message so its indentation is not mangled by an outer trimMargin.
private fun suppressionFooter(diagnosticId: String, downgradable: Boolean): String {
  val effect = if (downgradable) {
    "downgrade this error to a warning"
  } else {
    "silence this warning"
  }
  return """
    |  Diagnostic id: $diagnosticId
    |  To $effect, add to gradle.properties (comma-separate multiple ids):
    |    ${RoborazziDiagnosticSuppression.PROPERTY}=$diagnosticId
  """.trimMargin()
}

// Joins a trimMargin'd message body with a suppression footer as separate blocks.
private fun withFooter(messageBody: String, footer: String): String =
  messageBody + "\n" + footer

// First Gradle version that renders JUnit Platform file attachments in the test report.
// Below this, EngineExecutionListener.fileEntryPublished() is a silent no-op.
private val GradleTestReportAttachmentsMinVersion = GradleVersion.version("9.4")

/**
 * Diagnoses roborazzi-junit-platform-reporting setup mistakes for a single [test] task.
 *
 * Only diagnoses tasks whose runtime classpath actually contains the reporting module, so
 * projects that do not use the feature see nothing. Each problem is a single self-contained
 * block (problem, impact, copy-pasteable fix, docs link) prefixed with
 * [JUnitPlatformReportingLogPrefix] so it is easy to grep.
 *
 * Three of the four problems are warnings (ids [OldGradleDiagnosticId],
 * [NotJUnitPlatformDiagnosticId], and [EngineNotSelectedDiagnosticId] — the latter fires when
 * the engine selection leaves roborazzi-vintage out of the run so the feature is inert). The
 * remaining one ([DoubleExecutionDiagnosticId]) — the stock junit-vintage engine running
 * alongside roborazzi-vintage, which double-executes every test — is a build error by
 * default, because no valid configuration wants both engines to run the same tests. Listing
 * an id in [suppressedDiagnostics] (from the [RoborazziDiagnosticSuppression.PROPERTY] Gradle
 * property) silences a warning outright and downgrades the error to a warning.
 *
 * Runs at execution time (from a doFirst), so reading [Test.getClasspath] and
 * [Test.getOptions] happens after the classpath and test framework are resolved and never
 * forces configuration resolution during configuration time. As a consequence the
 * diagnostics only appear when the test task actually executes: when it is UP-TO-DATE or
 * served FROM-CACHE, doFirst does not run and nothing is reported (acceptable — a cached run
 * changed nothing to warn about).
 */
internal fun diagnoseJUnitPlatformReporting(test: Test, suppressedDiagnostics: Set<String>) {
  // Note: file-name based detection is best-effort; shading or renaming the jar can cause a
  // false negative (feature silently undetected).
  val hasReportingModule = test.classpath.any { file ->
    file.name.contains(JUnitPlatformReportingArtifactName)
  }
  if (!hasReportingModule) return

  val logger = test.logger

  // 1. Gradle too old to render attachments. Independent of the JUnit Platform setup:
  // even a correctly configured project silently produces no attachments here.
  if (GradleVersion.current() < GradleTestReportAttachmentsMinVersion &&
    !RoborazziDiagnosticSuppression.isSuppressed(suppressedDiagnostics, OldGradleDiagnosticId)
  ) {
    logger.warn(
      withFooter(
        """
        |$JUnitPlatformReportingLogPrefix screenshot attachments require Gradle ${GradleTestReportAttachmentsMinVersion.version}+, but this build is running Gradle ${GradleVersion.current().version}.
        |  Impact: your tests still run and pass, but no screenshots are attached to the Gradle test report (JUnit Platform's fileEntryPublished() is a silent no-op below ${GradleTestReportAttachmentsMinVersion.version}).
        |  Fix: upgrade the Gradle wrapper, e.g.
        |    ./gradlew wrapper --gradle-version ${GradleTestReportAttachmentsMinVersion.version}
        |  Docs: $JUnitPlatformReportingDocsUrl
        """.trimMargin(),
        suppressionFooter(OldGradleDiagnosticId, downgradable = false)
      )
    )
  }

  // 2. Not running on the JUnit Platform: roborazzi-vintage never runs, so the feature is
  // completely off even though its dependency is present.
  val options = test.options
  if (options !is JUnitPlatformOptions) {
    if (!RoborazziDiagnosticSuppression.isSuppressed(suppressedDiagnostics, NotJUnitPlatformDiagnosticId)) {
      logger.warn(
        withFooter(
          """
          |$JUnitPlatformReportingLogPrefix $JUnitPlatformReportingArtifactName is on the test classpath, but this Test task does not run on the JUnit Platform, so screenshot attachments are completely disabled.
          |  Impact: the roborazzi-vintage engine never runs; no screenshots are attached and the report is unchanged.
          |  Fix: switch this Test task to the JUnit Platform and exclude the stock junit-vintage engine. Add to build.gradle(.kts):
          |    // Kotlin DSL
          |    tasks.withType<Test>().configureEach {
          |      useJUnitPlatform {
          |        excludeEngines("$JUnitVintageEngineId")
          |      }
          |    }
          |    // Groovy DSL
          |    tasks.withType(Test).configureEach {
          |      useJUnitPlatform {
          |        excludeEngines '$JUnitVintageEngineId'
          |      }
          |    }
          |  Docs: $JUnitPlatformReportingDocsUrl
          """.trimMargin(),
          suppressionFooter(NotJUnitPlatformDiagnosticId, downgradable = false)
        )
      )
    }
    return
  }

  // 3. On the JUnit Platform: apply the SAME engine-selection predicate to both engines. An
  // engine runs only when it is not excluded AND allowed by the includeEngines filter (an
  // empty filter allows all; a non-empty one allows only the listed engines).
  fun selected(engineId: String): Boolean =
    engineId !in options.excludeEngines &&
      (options.includeEngines.isEmpty() || engineId in options.includeEngines)
  val vintageRuns = selected(JUnitVintageEngineId)
  val roborazziRuns = selected(RoborazziVintageEngineId)

  if (vintageRuns && roborazziRuns) {
    // Both the stock engine and roborazzi-vintage run the same tests: double execution, a
    // 100% misconfiguration. A build error by default; listing the id in the suppress
    // property downgrades it to a warning.
    val downgraded =
      RoborazziDiagnosticSuppression.isSuppressed(suppressedDiagnostics, DoubleExecutionDiagnosticId)
    val message =
      withFooter(
        """
        |$JUnitPlatformReportingLogPrefix the stock '$JUnitVintageEngineId' engine still runs alongside $RoborazziVintageEngineId, so every test runs twice.
        |  Impact: silent duplicate execution; the extra run writes a second, suffixed golden image (e.g. MyTest_2.png).
        |  Fix: exclude the stock engine inside useJUnitPlatform. In build.gradle(.kts):
        |    // Kotlin DSL
        |    useJUnitPlatform {
        |      excludeEngines("$JUnitVintageEngineId")
        |    }
        |    // Groovy DSL
        |    useJUnitPlatform {
        |      excludeEngines '$JUnitVintageEngineId'
        |    }
        |  (Alternatively restrict execution to this module's engine with includeEngines("$RoborazziVintageEngineId").)
        |  This is a build error because no valid configuration runs both engines over the same tests.
        |  Docs: $JUnitPlatformReportingDocsUrl
        """.trimMargin(),
        suppressionFooter(DoubleExecutionDiagnosticId, downgradable = true)
      )
    if (downgraded) {
      logger.warn(message)
    } else {
      throw GradleException(message)
    }
  } else if (!roborazziRuns &&
    !RoborazziDiagnosticSuppression.isSuppressed(suppressedDiagnostics, EngineNotSelectedDiagnosticId)
  ) {
    // useJUnitPlatform is enabled and the module is on the classpath, but the engine
    // selection (excludeEngines/includeEngines) leaves roborazzi-vintage out of the run, so
    // the attachment feature is entirely inert. (The all-normal case — only roborazzi-vintage
    // runs — falls through both branches and reports nothing.)
    logger.warn(
      withFooter(
        """
        |$JUnitPlatformReportingLogPrefix the roborazzi-vintage engine is not in the selected engine set, so screenshot attachments are completely disabled.
        |  Impact: the roborazzi-vintage engine never runs; no screenshots are attached and the report is unchanged.
        |  Fix: keep roborazzi-vintage in the selected set — remove it from excludeEngines, or if you use includeEngines make sure it is listed. In build.gradle(.kts):
        |    // Kotlin DSL
        |    useJUnitPlatform {
        |      includeEngines("$RoborazziVintageEngineId")
        |    }
        |    // Groovy DSL
        |    useJUnitPlatform {
        |      includeEngines '$RoborazziVintageEngineId'
        |    }
        |  Docs: $JUnitPlatformReportingDocsUrl
        """.trimMargin(),
        suppressionFooter(EngineNotSelectedDiagnosticId, downgradable = false)
      )
    )
  }
}
