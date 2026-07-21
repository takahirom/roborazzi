package io.github.takahirom.roborazzi

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File

/**
 * Integration tests for the roborazzi-junit-platform-reporting module: a wrapping
 * TestEngine (`roborazzi-vintage`) that publishes Roborazzi's captured images via
 * JUnit Platform's `EngineExecutionListener.fileEntryPublished()`, which Gradle 9.4+
 * attaches to its test report.
 *
 * Run with:
 * ./gradlew roborazzi-gradle-plugin:integrationTest --tests "*JUnitPlatformReportingTest*"
 */
class JUnitPlatformReportingTest {

  private companion object {
    // Stable prefix the plugin puts on every JUnit Platform reporting setup diagnostic. Kept
    // in sync with JUnitPlatformReportingLogPrefix in JUnitPlatformReportingDiagnostics.kt.
    const val JUNIT_PLATFORM_REPORTING_LOG_PREFIX = "Roborazzi JUnit Platform reporting:"

    // The diagnostics run in the test task's doFirst, which only executes when the task is
    // not served from the (suite-shared) build cache. Diagnostic assertions pass this so the
    // inner build always runs the test task; it overrides the harness's --build-cache because
    // it is appended after it on the command line (last flag wins).
    const val NO_BUILD_CACHE = "--no-build-cache"
  }

  @get:Rule
  val testProjectDir = TemporaryFolder()

  @get:Rule
  val testNameOutputRule = object : TestWatcher() {
    override fun starting(description: Description?) {
      println("JUnitPlatformReportingTest.${description?.methodName} started")
    }

    override fun finished(description: Description?) {
      super.finished(description)
      println("JUnitPlatformReportingTest.${description?.methodName} finished")
    }
  }

  private val testResultsXmlDir
    get() = File(testProjectDir.root, "app/build/test-results/testDebugUnitTest")

  private val testReportsHtmlDir
    get() = File(testProjectDir.root, "app/build/reports/tests/testDebugUnitTest")

  /** Concatenated text of every JUnit XML result file for the unit test task. */
  private fun junitXml(): String =
    testResultsXmlDir.walkTopDown()
      .filter { it.isFile && it.extension == "xml" }
      .joinToString("\n") { it.readText() }

  /** Concatenated text of every generated HTML report page (excluding css/js assets). */
  private fun htmlReport(): String =
    testReportsHtmlDir.walkTopDown()
      .filter { it.isFile && it.extension == "html" }
      .filter { it.parentFile.name != "css" && it.parentFile.name != "js" }
      .joinToString("\n") { it.readText() }

  /**
   * (a) Current Gradle (the wrapper, 9.5.1) with reporting enabled: a record followed by
   * a failing verify must attach the _actual and _compare images to the test report.
   *
   * Gradle 9.4+ does not copy the images into the report directory; it renders them as
   * attachments that reference the original files under build/outputs/roborazzi. So we
   * assert on the attachment markers in the JUnit XML ([[ATTACHMENT|...]]) and on the
   * <img> tags in the HTML report, not on copied png files.
   */
  @Test
  fun attachesImagesOnCurrentGradle() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      buildGradle.enableJUnitPlatformReporting = true
      // --no-build-cache forces the inner testDebugUnitTest task to actually execute so the
      // doFirst diagnostic runs. The TestKit build cache is shared across the whole suite, so
      // without this a cache hit would skip the test task (and its doFirst) and the
      // "no warning" assertion below would pass vacuously. See NO_BUILD_CACHE.
      val recordResult = recordWithParams(NO_BUILD_CACHE)
      changeScreen()
      val verifyResult = verifyAndFail(NO_BUILD_CACHE)
      verifyResult.shouldDetectChangedPngCapture()

      // A correctly configured project must not trip any of the setup diagnostics
      // (guards against false positives). The prefix is the stable grep anchor shared by
      // every JUnit Platform reporting warning.
      assert(!recordResult.output.contains(JUNIT_PLATFORM_REPORTING_LOG_PREFIX)) {
        "Expected no JUnit Platform reporting warning on the record run, but found one.\n${recordResult.output}"
      }
      assert(!verifyResult.output.contains(JUNIT_PLATFORM_REPORTING_LOG_PREFIX)) {
        "Expected no JUnit Platform reporting warning on the verify run, but found one.\n${verifyResult.output}"
      }

      val xml = junitXml()
      assert(xml.contains("[[ATTACHMENT|")) {
        "Expected JUnit XML to contain an [[ATTACHMENT|...]] marker but it did not.\n$xml"
      }
      assert(xml.contains("testCapture_compare.png")) {
        "Expected the compare image to be attached in the JUnit XML.\n$xml"
      }
      assert(xml.contains("testCapture_actual.png")) {
        "Expected the actual image to be attached in the JUnit XML.\n$xml"
      }
      // The golden itself is also attached when it exists (record wrote it before the
      // failing verify). Match the golden strictly inside an [[ATTACHMENT|...]] marker
      // whose filename ends in "testCapture.png" — the ".png" right after "testCapture"
      // excludes the "_compare"/"_actual" variants, and scoping to the ATTACHMENT marker
      // excludes the golden path that also appears in the failure log / system-out.
      assert(Regex("""\[\[ATTACHMENT\|[^\]]*testCapture\.png""").containsMatchIn(xml)) {
        "Expected the golden image (testCapture.png) to be attached in the JUnit XML, not only the _compare/_actual variants.\n$xml"
      }

      val html = htmlReport()
      assert(html.contains("class=\"attachments\"")) {
        "Expected the HTML report to contain an attachments section.\n$html"
      }
      assert(Regex("<img[^>]*testCapture_compare\\.png").containsMatchIn(html)) {
        "Expected the HTML report to embed the compare image.\n$html"
      }
      assert(Regex("<img[^>]*testCapture_actual\\.png").containsMatchIn(html)) {
        "Expected the HTML report to embed the actual image.\n$html"
      }
      // The golden is embedded too. "testCapture\.png" (literal dot) matches only the
      // golden filename, not testCapture_compare.png / testCapture_actual.png.
      assert(Regex("<img[^>]*testCapture\\.png").containsMatchIn(html)) {
        "Expected the HTML report to embed the golden image (testCapture.png).\n$html"
      }
    }
  }

  /**
   * (b) Older Gradle (predating the 9.4 test-report attachments) with reporting enabled:
   * that Gradle cannot render attachments (the platform's fileEntryPublished() is a no-op),
   * so the reporting module cannot do its job. Adding the dependency is an explicit request
   * for attachments, so the plugin fails the build with an error that names the 9.4
   * requirement rather than silently producing no attachments. Suppressing the id downgrades
   * the error to a warning: the build then completes and records normally, for teams that
   * have not yet upgraded the wrapper.
   *
   * We cannot use Gradle 8.13 (AGP 8.13's nominal minimum) here: the shared integration
   * test project's root build.gradle.kts uses Test.failOnNoDiscoveredTests, which is a
   * Gradle 9.0+ API, so the project fails to even configure on 8.13 (unresolved
   * reference) for reasons unrelated to this module. Gradle 9.0.0 is therefore the
   * oldest version the project supports, and it is still below the 9.4 attachment
   * threshold, so it exercises this path. Both invocations reuse the same 9.0.0
   * distribution so it is downloaded at most once.
   */
  @Test
  fun failsBuildOnOldGradleAndSuppressDowngradesToWarning() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      gradleVersion = "9.0.0"
      buildGradle.enableJUnitPlatformReporting = true

      // Without suppression the build fails with the old-Gradle error before tests run.
      val failure = recordAndFail(NO_BUILD_CACHE)
      assert(failure.output.contains(JUNIT_PLATFORM_REPORTING_LOG_PREFIX)) {
        "Expected a JUnit Platform reporting error on old Gradle, but found none.\n${failure.output}"
      }
      assert(failure.output.contains("require Gradle 9.4+")) {
        "Expected the old-Gradle error to name the 9.4 requirement.\n${failure.output}"
      }
      assert(failure.output.contains("Diagnostic id: junitPlatformReporting.oldGradle")) {
        "Expected the error to print its stable, namespaced diagnostic id.\n${failure.output}"
      }
      assert(failure.output.contains("roborazzi.suppress=junitPlatformReporting.oldGradle")) {
        "Expected the error to explain how to downgrade it via the roborazzi.suppress property.\n${failure.output}"
      }

      // Suppressing the id downgrades the error to a warning: the build completes and records
      // the golden even though attachments cannot render on this Gradle.
      val suppressed =
        recordWithParams("-Proborazzi.suppress=junitPlatformReporting.oldGradle", NO_BUILD_CACHE)
      assert(suppressed.output.contains(JUNIT_PLATFORM_REPORTING_LOG_PREFIX)) {
        "Expected the downgraded old-Gradle warning to still surface, but found none.\n${suppressed.output}"
      }
      assert(suppressed.output.contains("require Gradle 9.4+")) {
        "Expected the downgraded warning to name the 9.4 requirement.\n${suppressed.output}"
      }
      checkRecordedFileExists(
        "app/build/outputs/roborazzi/com.github.takahirom.integration_test_project.RoborazziTest.testCapture.png"
      )
    }
  }

  /**
   * (c) The excludeEngines footgun is now a build error. With reporting enabled and
   * useJUnitPlatform but WITHOUT excludeEngines("junit-vintage"), both the stock
   * junit-vintage engine and roborazzi-vintage would discover the same JUnit4 tests and run
   * every test twice. Since no valid configuration wants that, the plugin fails the build
   * (from the test task's doFirst, before the duplicate execution happens) with a message
   * that names the problem and the copy-pasteable fix.
   */
  @Test
  fun failsBuildOnDoubleExecutionWithoutExcludeEngines() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      buildGradle.enableJUnitPlatformReporting = true
      buildGradle.excludeVintageEngine = false
      val result = recordAndFail(NO_BUILD_CACHE)

      assert(result.output.contains(JUNIT_PLATFORM_REPORTING_LOG_PREFIX)) {
        "Expected a JUnit Platform reporting error about the double execution, but found none.\n${result.output}"
      }
      assert(result.output.contains("every test runs twice")) {
        "Expected the error to explain the double execution.\n${result.output}"
      }
      assert(result.output.contains("excludeEngines(\"junit-vintage\")")) {
        "Expected the error to include the copy-pasteable excludeEngines fix.\n${result.output}"
      }
      assert(result.output.contains("Diagnostic id: junitPlatformReporting.doubleExecution")) {
        "Expected the error to print its stable, namespaced diagnostic id.\n${result.output}"
      }
      assert(result.output.contains("roborazzi.suppress=junitPlatformReporting.doubleExecution")) {
        "Expected the error to explain how to suppress it via the roborazzi.suppress property.\n${result.output}"
      }
    }
  }

  /**
   * (c2) includeEngines("roborazzi-vintage") is a correct alternative to excludeEngines: it
   * keeps the stock junit-vintage engine out of the execution set, so there is no double
   * execution. The plugin must NOT flag it (false-positive guard) and the build must record
   * a single golden with no _2 variant.
   */
  @Test
  fun doesNotFlagIncludeEnginesRoborazziVintage() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      buildGradle.enableJUnitPlatformReporting = true
      buildGradle.includeRoborazziVintageEngine = true
      val result = recordWithParams(NO_BUILD_CACHE)

      assert(!result.output.contains(JUNIT_PLATFORM_REPORTING_LOG_PREFIX)) {
        "Expected no JUnit Platform reporting diagnostic for includeEngines(\"roborazzi-vintage\"), but found one.\n${result.output}"
      }
      checkRecordedFileExists(
        "app/build/outputs/roborazzi/com.github.takahirom.integration_test_project.RoborazziTest.testCapture.png"
      )
      checkRecordedFileNotExists(
        "app/build/outputs/roborazzi/com.github.takahirom.integration_test_project.RoborazziTest.testCapture_2.png"
      )
    }
  }

  /**
   * (c3) Suppressing the doubleExecution id downgrades the build error to a warning:
   * roborazzi.suppress=junitPlatformReporting.doubleExecution. The build then succeeds and
   * the tests run twice (the warned-about behavior), proving suppression relaxed the error
   * rather than fixing the configuration.
   */
  @Test
  fun suppressingDoubleExecutionDowngradesErrorToWarning() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      buildGradle.enableJUnitPlatformReporting = true
      buildGradle.excludeVintageEngine = false
      val result =
        recordWithParams("-Proborazzi.suppress=junitPlatformReporting.doubleExecution", NO_BUILD_CACHE)

      // Build succeeds (no exception thrown) but the warning is still surfaced.
      assert(result.output.contains(JUNIT_PLATFORM_REPORTING_LOG_PREFIX)) {
        "Expected the downgraded double-execution warning, but found none.\n${result.output}"
      }
      assert(result.output.contains("every test runs twice")) {
        "Expected the warning to explain the double execution.\n${result.output}"
      }
      // With the error relaxed to a warning, both engines still run, so the test executes
      // twice and Roborazzi writes the _2 variant.
      val xml = junitXml()
      assert(xml.contains("tests=\"2\"")) {
        "Expected the single test to run twice (once per engine) when only warning, i.e. tests=\"2\".\n$xml"
      }
      checkRecordedFileExists(
        "app/build/outputs/roborazzi/com.github.takahirom.integration_test_project.RoborazziTest.testCapture_2.png"
      )
    }
  }

  /**
   * (d) The reporting dependency is on the test classpath but the Test task was never
   * switched to the JUnit Platform (no useJUnitPlatform). roborazzi-vintage therefore never
   * runs and the whole feature is off, so the plugin fails the build with an error naming the
   * problem. Suppressing the id downgrades the error to a warning: the build then completes
   * and the tests run normally on plain JUnit4 — exactly once (no double execution), so a
   * single golden is recorded and no _2 variant is written.
   */
  @Test
  fun failsBuildWithoutJUnitPlatformAndSuppressRunsOnce() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      buildGradle.enableJUnitPlatformReporting = true
      buildGradle.applyJUnitPlatform = false

      // Without suppression the build fails because the task is not on the JUnit Platform.
      val failure = recordAndFail(NO_BUILD_CACHE)
      assert(failure.output.contains(JUNIT_PLATFORM_REPORTING_LOG_PREFIX)) {
        "Expected a JUnit Platform reporting error when useJUnitPlatform is missing, but found none.\n${failure.output}"
      }
      assert(failure.output.contains("does not run on the JUnit Platform")) {
        "Expected the error to explain that the task is not on the JUnit Platform.\n${failure.output}"
      }
      assert(failure.output.contains("Diagnostic id: junitPlatformReporting.notJUnitPlatform")) {
        "Expected the error to print its stable, namespaced diagnostic id.\n${failure.output}"
      }

      // Suppressing the id downgrades the error to a warning (the message still surfaces),
      // and the build completes: plain JUnit4 runs the single test once, no second engine,
      // no _2 golden.
      val suppressed =
        recordWithParams("-Proborazzi.suppress=junitPlatformReporting.notJUnitPlatform", NO_BUILD_CACHE)
      assert(suppressed.output.contains(JUNIT_PLATFORM_REPORTING_LOG_PREFIX)) {
        "Expected the downgraded notJUnitPlatform warning to still surface, but found none.\n${suppressed.output}"
      }
      assert(suppressed.output.contains("does not run on the JUnit Platform")) {
        "Expected the downgraded warning to explain that the task is not on the JUnit Platform.\n${suppressed.output}"
      }
      val xml = junitXml()
      assert(xml.contains("tests=\"1\"")) {
        "Expected the single test to run once on plain JUnit4, i.e. tests=\"1\".\n$xml"
      }
      checkRecordedFileExists(
        "app/build/outputs/roborazzi/com.github.takahirom.integration_test_project.RoborazziTest.testCapture.png"
      )
      checkRecordedFileNotExists(
        "app/build/outputs/roborazzi/com.github.takahirom.integration_test_project.RoborazziTest.testCapture_2.png"
      )
    }
  }

  /**
   * (f) excludeEngines("roborazzi-vintage") is a misconfiguration, not a double execution:
   * the stock junit-vintage engine still runs the tests, but roborazzi-vintage is filtered
   * out of the execution set, so no images are ever published. That inert configuration is
   * now a build error (engineNotSelected), not a warning — but it must NOT be misreported as
   * the doubleExecution error the old implementation false-positived here.
   *
   * Suppressing the engineNotSelected id downgrades the error to a warning: the build then
   * completes. Because roborazzi-vintage never runs, only the stock engine records the single
   * golden and no _2 duplicate is written.
   */
  @Test
  fun failsBuildOnExcludeRoborazziVintageAndSuppressDowngradesToWarning() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      buildGradle.enableJUnitPlatformReporting = true
      buildGradle.excludeRoborazziVintageEngine = true

      // Without suppression the build fails with the engineNotSelected error.
      val failure = recordAndFail(NO_BUILD_CACHE)
      // It must be the engineNotSelected error, not the doubleExecution false positive.
      assert(!failure.output.contains("Diagnostic id: junitPlatformReporting.doubleExecution")) {
        "Expected no doubleExecution diagnostic for excludeEngines(\"roborazzi-vintage\"), but found one.\n${failure.output}"
      }
      assert(failure.output.contains(JUNIT_PLATFORM_REPORTING_LOG_PREFIX)) {
        "Expected a JUnit Platform reporting error, but found none.\n${failure.output}"
      }
      assert(failure.output.contains("the roborazzi-vintage engine is not in the selected engine set")) {
        "Expected the engineNotSelected error body text.\n${failure.output}"
      }
      assert(failure.output.contains("Diagnostic id: junitPlatformReporting.engineNotSelected")) {
        "Expected the engineNotSelected error to print its stable, namespaced diagnostic id.\n${failure.output}"
      }

      // Suppressing the id downgrades the error to a warning (still surfaced), and the build
      // completes: only the stock engine ran, so a single golden was recorded and no _2.
      val suppressed =
        recordWithParams("-Proborazzi.suppress=junitPlatformReporting.engineNotSelected", NO_BUILD_CACHE)
      assert(suppressed.output.contains(JUNIT_PLATFORM_REPORTING_LOG_PREFIX)) {
        "Expected the downgraded engineNotSelected warning to still surface, but found none.\n${suppressed.output}"
      }
      assert(suppressed.output.contains("the roborazzi-vintage engine is not in the selected engine set")) {
        "Expected the downgraded warning body text.\n${suppressed.output}"
      }
      checkRecordedFileExists(
        "app/build/outputs/roborazzi/com.github.takahirom.integration_test_project.RoborazziTest.testCapture.png"
      )
      checkRecordedFileNotExists(
        "app/build/outputs/roborazzi/com.github.takahirom.integration_test_project.RoborazziTest.testCapture_2.png"
      )
    }
  }
}
