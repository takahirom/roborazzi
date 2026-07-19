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
      record()
      changeScreen()
      verifyAndFail().shouldDetectChangedPngCapture()

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
    }
  }

  /**
   * (b) Older Gradle (predating the 9.4 test-report attachments) with reporting enabled:
   * the same record -> failing verify scenario must still run to completion. The verify
   * is expected to fail (that is the scenario), but the reporting integration must not
   * add any failure of its own even though this Gradle cannot render attachments; the
   * platform's fileEntryPublished() default is a no-op there.
   *
   * We cannot use Gradle 8.13 (AGP 8.13's nominal minimum) here: the shared integration
   * test project's root build.gradle.kts uses Test.failOnNoDiscoveredTests, which is a
   * Gradle 9.0+ API, so the project fails to even configure on 8.13 (unresolved
   * reference) for reasons unrelated to this module. Gradle 9.0.0 is therefore the
   * oldest version the project supports, and it is still below the 9.4 attachment
   * threshold, so it exercises the graceful-degradation path.
   */
  @Test
  fun runsWithoutErrorOnOldGradle() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      gradleVersion = "9.0.0"
      buildGradle.enableJUnitPlatformReporting = true
      record()
      changeScreen()
      // The verify still fails on the changed image, but the build must not fail for any
      // other reason (e.g. an exception thrown from the reporting listener).
      val result = verifyAndFail()
      result.shouldDetectChangedPngCapture()
    }
  }

  /**
   * (c) Baseline for the excludeEngines footgun. With reporting enabled but WITHOUT
   * excludeEngines("junit-vintage"), both the stock junit-vintage engine and
   * roborazzi-vintage discover the same JUnit4 tests, so every test runs twice.
   *
   * This is currently silent (the record succeeds), which is exactly why the setup docs
   * insist on excludeEngines. This test pins that observed behavior so a future
   * diagnostic (e.g. detecting and warning about the double engine) has a baseline to
   * change: the single testCapture runs twice (tests="2") and Roborazzi writes a second,
   * suffixed golden image for the duplicate run.
   */
  @Test
  fun runsTwiceWithoutExcludeEngines() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      buildGradle.enableJUnitPlatformReporting = true
      buildGradle.excludeVintageEngine = false
      // Record succeeds despite the duplicate execution (no error is surfaced today).
      record()

      val xml = junitXml()
      assert(xml.contains("tests=\"2\"")) {
        "Expected the single test to run twice (once per engine), i.e. tests=\"2\".\n$xml"
      }
      val testCaseCount = Regex("<testcase name=\"testCapture\"").findAll(xml).count()
      assert(testCaseCount == 2) {
        "Expected two testCapture testcases from double execution, but found $testCaseCount.\n$xml"
      }
      // The duplicate run collides on the golden path, so Roborazzi writes a _2 variant.
      checkRecordedFileExists(
        "app/build/outputs/roborazzi/com.github.takahirom.integration_test_project.RoborazziTest.testCapture.png"
      )
      checkRecordedFileExists(
        "app/build/outputs/roborazzi/com.github.takahirom.integration_test_project.RoborazziTest.testCapture_2.png"
      )
    }
  }
}
