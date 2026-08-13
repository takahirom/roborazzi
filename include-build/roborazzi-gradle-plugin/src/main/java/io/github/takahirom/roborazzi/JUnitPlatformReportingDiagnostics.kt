package io.github.takahirom.roborazzi

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.testing.AbstractTestTask
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
 * feature, e.g. `junitPlatformReporting.doubleExecution`. What suppression does is up to each
 * diagnostic: every JUnit Platform reporting diagnostic is a build error that suppression
 * downgrades to a warning (rather than silencing outright), so the message still surfaces.
 * Callers resolve the id set once (a Gradle property is a Configuration Cache input) and
 * capture only that Set<String>, so no Project reference leaks into task actions.
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

// The tail appended to every diagnostic message: its id and how to suppress it. Every
// diagnostic is a build error, and suppression downgrades it to a warning (rather than
// silencing it), so the footer always explains that effect. Returned as a standalone block
// that callers concatenate onto the already-trimMargin'd message so its indentation is not
// mangled by an outer trimMargin.
private fun suppressionFooter(diagnosticId: String): String =
  """
    |  Diagnostic id: $diagnosticId
    |  To downgrade this error to a warning, add to gradle.properties (comma-separate multiple ids):
    |    ${RoborazziDiagnosticSuppression.PROPERTY}=$diagnosticId
  """.trimMargin()

// Joins a trimMargin'd message body with a suppression footer as separate blocks.
private fun withFooter(messageBody: String, footer: String): String =
  messageBody + "\n" + footer

// Emits one diagnostic. Every diagnostic is a build error by default; listing its id in the
// suppress property downgrades it to a warning (the message still surfaces, the build no
// longer fails). [messageBody] is an already-trimMargin'd block; this appends the shared
// suppression footer before reporting.
private fun report(
  logger: Logger,
  suppressedDiagnostics: Set<String>,
  diagnosticId: String,
  messageBody: String,
) {
  val message = withFooter(messageBody, suppressionFooter(diagnosticId))
  if (RoborazziDiagnosticSuppression.isSuppressed(suppressedDiagnostics, diagnosticId)) {
    logger.warn(message)
  } else {
    throw GradleException(message)
  }
}

// First Gradle version that renders JUnit Platform file attachments in the test report.
// Below this, EngineExecutionListener.fileEntryPublished() is a silent no-op.
private val GradleTestReportAttachmentsMinVersion = GradleVersion.version("9.4")

/**
 * Registers the configuration-phase half of the JUnit Platform reporting diagnostics (layer 1).
 *
 * Detects the reporting module by scanning **declared** dependency coordinates — never by
 * resolving a configuration — following the same approach as
 * `verifyLibraryDependencies` in AndroidGeneratePreviewTestsConfigurator.kt. When the module is
 * declared, every wired [Test] task is inspected right there by the shared
 * [collectJUnitPlatformReportingDiagnostics] and reported through the shared [report], so a setup
 * mistake fails the build during configuration — before any task runs, and therefore even for
 * invocations like `./gradlew help` that never execute a test.
 *
 * Hook choice: [org.gradle.api.invocation.Gradle.projectsEvaluated], not
 * [Project.afterEvaluate]. Both `useJUnitPlatform { }` and engine filters are frequently applied
 * from inside someone else's `afterEvaluate` (a convention plugin, or the user's own build
 * script). Inspecting [Test.getOptions] from our own `afterEvaluate` could therefore observe a
 * task that has not yet been switched to the JUnit Platform and report a false
 * [NotJUnitPlatformDiagnosticId]. `projectsEvaluated` runs after every project's `afterEvaluate`
 * callbacks in both single- and multi-project builds, so it is the latest hook that is still
 * independent of which tasks were requested — unlike `taskGraph.whenReady`, it fires the same way
 * for `help`, `tasks`, and `test`.
 *
 * Configuration Cache: this all happens during configuration, so a CC hit replays nothing and
 * reports nothing (accepted: a cached configuration was already diagnosed when it was stored).
 * Nothing here is captured by a task action, so no CC incompatibility is introduced.
 *
 * [detectedFromDeclaredDependency] is set to true once the declared scan finds the module. The
 * execution-time layer reads it and stays quiet, so a single mistake is reported once rather than
 * twice. [testTaskCollections] is read (not copied) inside the callback because
 * `configureRoborazziTasks` keeps appending to it while projects are still being evaluated.
 */
internal fun registerJUnitPlatformReportingConfigurationDiagnostics(
  project: Project,
  testTaskCollections: List<TaskCollection<out AbstractTestTask>>,
  suppressedDiagnostics: Set<String>,
  detectedFromDeclaredDependency: Property<Boolean>,
) {
  project.gradle.projectsEvaluated {
    if (!hasDeclaredJUnitPlatformReportingDependency(project)) return@projectsEvaluated
    detectedFromDeclaredDependency.set(true)
    // Realizing the Test tasks is what makes the shift-left possible, and it is deliberately
    // done only after the declared-dependency scan says this project opted into the feature, so
    // projects that do not use it pay nothing.
    val tests = testTaskCollections
      .flatMap { collection -> collection.filterIsInstance<Test>() }
      .distinct()
    // A project has one Test task per variant (testDebugUnitTest, testReleaseUnitTest, ...) and
    // the JUnit Platform setup is normally applied to all of them at once via
    // tasks.withType<Test>(), so every task produces the identical diagnostic. Report each
    // distinct diagnostic once instead of once per variant: the user has a single thing to fix.
    tests
      .flatMap { test -> collectJUnitPlatformReportingDiagnostics(test) }
      .distinct()
      .forEach { diagnostic ->
        report(project.logger, suppressedDiagnostics, diagnostic.id, diagnostic.messageBody)
      }
  }
}

/**
 * True when this project declares a dependency on the reporting module in a test-scoped
 * configuration.
 *
 * Only reads declared dependency coordinates (`Configuration.getDependencies()`), so no
 * configuration is resolved at configuration time. Test-scoped is matched by configuration name
 * so the many spellings are all covered without enumerating them: `testImplementation`,
 * `testRuntimeOnly`, the variant-specific `testDebugImplementation`, and the KMP
 * `androidUnitTestImplementation` / `jvmTestImplementation`. Restricting to test-scoped
 * configurations avoids reporting on a `compileOnly`-style declaration that never reaches a test
 * runtime classpath.
 *
 * This detection is intentionally narrower than the execution-time classpath scan: a module that
 * arrives only transitively, via another library, is invisible here and is left to layer 2.
 */
private fun hasDeclaredJUnitPlatformReportingDependency(project: Project): Boolean =
  project.configurations
    .filter { configuration -> configuration.name.contains("test", ignoreCase = true) }
    .any { configuration ->
      configuration.dependencies.any { dependency ->
        dependency.name == JUnitPlatformReportingArtifactName
      }
    }

/**
 * Diagnoses roborazzi-junit-platform-reporting setup mistakes for a single [test] task at
 * execution time (layer 2, the backstop).
 *
 * Detects the reporting module by scanning the resolved test runtime classpath, which catches the
 * case layer 1 cannot see: the module pulled in transitively through another dependency rather
 * than declared directly. Runs from a doFirst so reading [Test.getClasspath] and
 * [Test.getOptions] happens after the classpath and test framework are resolved and never forces
 * configuration resolution during configuration time. As a consequence this layer only speaks
 * when the test task actually executes: when it is UP-TO-DATE or served FROM-CACHE, doFirst does
 * not run and nothing is reported (acceptable — a cached run changed nothing to warn about).
 *
 * [detectedFromDeclaredDependency] is the layer 1 result. When it is true the same mistake was
 * already reported (or deliberately downgraded) during configuration, so this layer stays silent
 * instead of printing everything a second time.
 */
internal fun diagnoseJUnitPlatformReporting(
  test: Test,
  suppressedDiagnostics: Set<String>,
  detectedFromDeclaredDependency: Boolean,
) {
  if (detectedFromDeclaredDependency) return
  // Note: file-name based detection is best-effort; shading or renaming the jar can cause a
  // false negative (feature silently undetected).
  val hasReportingModule = test.classpath.any { file ->
    file.name.contains(JUnitPlatformReportingArtifactName)
  }
  if (!hasReportingModule) return

  collectJUnitPlatformReportingDiagnostics(test).forEach { diagnostic ->
    report(test.logger, suppressedDiagnostics, diagnostic.id, diagnostic.messageBody)
  }
}

// One problem found by [collectJUnitPlatformReportingDiagnostics]. Kept as data (rather than
// reported on the spot) so a caller inspecting several Test tasks can drop the duplicates every
// variant produces before anything reaches the log or throws. A data class so that equality is
// message equality, which is exactly what "the same problem" means here.
private data class JUnitPlatformReportingDiagnostic(val id: String, val messageBody: String)

/**
 * The single implementation of the four JUnit Platform reporting checks, shared by both layers.
 * Callers have already decided that this project uses the reporting module; this function only
 * decides whether its setup can actually produce attachments, and describes what it found.
 *
 * Each problem is returned as a single self-contained block (problem, impact, copy-pasteable fix,
 * docs link) prefixed with [JUnitPlatformReportingLogPrefix] so it is easy to grep. Severity and
 * suppression are not decided here: [report] applies them identically for both layers, so all four
 * problems ([OldGradleDiagnosticId], [NotJUnitPlatformDiagnosticId], [EngineNotSelectedDiagnosticId],
 * and [DoubleExecutionDiagnosticId]) are build errors that
 * [RoborazziDiagnosticSuppression.PROPERTY] can downgrade to warnings.
 */
private fun collectJUnitPlatformReportingDiagnostics(
  test: Test,
): List<JUnitPlatformReportingDiagnostic> {
  val diagnostics = mutableListOf<JUnitPlatformReportingDiagnostic>()
  fun found(diagnosticId: String, messageBody: String) {
    diagnostics.add(JUnitPlatformReportingDiagnostic(diagnosticId, messageBody))
  }

  // 1. Gradle too old to render attachments. Independent of the JUnit Platform setup:
  // even a correctly configured project produces no attachments here.
  if (GradleVersion.current() < GradleTestReportAttachmentsMinVersion) {
    found(
      OldGradleDiagnosticId,
      """
      |$JUnitPlatformReportingLogPrefix screenshot attachments require Gradle ${GradleTestReportAttachmentsMinVersion.version}+, but this build is running Gradle ${GradleVersion.current().version}.
      |  Impact: the tests would still run, but no screenshots would be attached to the Gradle test report (JUnit Platform's fileEntryPublished() is a silent no-op below ${GradleTestReportAttachmentsMinVersion.version}).
      |  Fix: upgrade the Gradle wrapper, e.g.
      |    ./gradlew wrapper --gradle-version ${GradleTestReportAttachmentsMinVersion.version}
      |  Docs: $JUnitPlatformReportingDocsUrl
      """.trimMargin()
    )
  }

  // 2. Not running on the JUnit Platform: roborazzi-vintage never runs, so the feature is
  // completely off even though its dependency is present.
  val options = test.options
  if (options !is JUnitPlatformOptions) {
    found(
      NotJUnitPlatformDiagnosticId,
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
      """.trimMargin()
    )
    // Nothing after this point is meaningful: the engine filters of a task that is not on the
    // JUnit Platform say nothing about which engines run.
    return diagnostics
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
    found(
      DoubleExecutionDiagnosticId,
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
      """.trimMargin()
    )
  } else if (!roborazziRuns) {
    // useJUnitPlatform is enabled and the module is on the classpath, but the engine
    // selection (excludeEngines/includeEngines) leaves roborazzi-vintage out of the run, so
    // the attachment feature is entirely inert. (The all-normal case — only roborazzi-vintage
    // runs — falls through both branches and reports nothing.)
    found(
      EngineNotSelectedDiagnosticId,
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
      """.trimMargin()
    )
  }
  return diagnostics
}
