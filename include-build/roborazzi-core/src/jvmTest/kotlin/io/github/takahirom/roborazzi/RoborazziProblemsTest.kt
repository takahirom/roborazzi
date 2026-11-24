package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziProblem
import com.github.takahirom.roborazzi.RoborazziProblemSeverity
import com.github.takahirom.roborazzi.RoborazziProblems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

@OptIn(InternalRoborazziApi::class)
class RoborazziProblemsTest {
  private val problem = RoborazziProblems.ComposePreviewRenderScaleMismatch
  private val key = "roborazzi.problemSeverity.composePreview.renderScaleMismatch"

  @Test
  fun noSettingKeepsDefaults() {
    assertEquals(
      emptyMap<String, RoborazziProblemSeverity>(),
      RoborazziProblems.parseSeverities(mapOf("roborazzi.test.record" to "true"))
    )
    assertEquals(RoborazziProblemSeverity.Error, RoborazziProblems.severityOf(problem, emptyMap()))
  }

  @Test
  fun aKeyPerProblemOverridesTheDefault() {
    val severities = RoborazziProblems.parseSeverities(
      mapOf(
        key to " warning ",
        "roborazzi.test.record" to "true",
      )
    )
    assertEquals(mapOf(problem.id to RoborazziProblemSeverity.Warning), severities)
    assertEquals(RoborazziProblemSeverity.Warning, RoborazziProblems.severityOf(problem, severities))
  }

  @Test
  fun invalidSettingsFail() {
    listOf(
      mapOf("roborazzi.problemSeverity.composePreview.renderScaleMisMatch" to "warning"),
      mapOf("roborazzi.problemSeverity." to "warning"),
      mapOf(key to "WARNING"),
      mapOf(key to ""),
      mapOf(key to null),
    ).forEach { properties ->
      assertThrows(properties.toString(), IllegalArgumentException::class.java) {
        RoborazziProblems.parseSeverities(properties)
      }
    }
  }

  @Test
  fun theTestJvmReadsOnlyTheKeyOfTheReportedProblem() {
    // A newer plugin may forward an ID that this library does not know yet. That must not fail
    // a test that reports a different problem.
    withSystemProperties(
      "roborazzi.problemSeverity.someArea.problemFromANewerVersion" to "warning",
      key to "warning",
    ) {
      assertEquals(RoborazziProblemSeverity.Warning, RoborazziProblems.severityFromSystemProperty(problem))
    }
    withSystemProperties(
      "roborazzi.problemSeverity.someArea.problemFromANewerVersion" to "warning",
    ) {
      assertEquals(RoborazziProblemSeverity.Error, RoborazziProblems.severityFromSystemProperty(problem))
    }
  }

  @Test
  fun everyDeclaredProblemIsConfigurable() {
    val declared = RoborazziProblems::class.java.declaredMethods
      .filter { it.returnType == RoborazziProblem::class.java && it.parameterCount == 0 }
      .map { it.invoke(RoborazziProblems) as RoborazziProblem }
    assertTrue(declared.isNotEmpty())
    assertEquals(declared.toSet(), RoborazziProblems.all.toSet())
    declared.forEach { declaredProblem ->
      assertEquals(
        RoborazziProblemSeverity.Disabled,
        RoborazziProblems.severityOf(
          declaredProblem,
          RoborazziProblems.parseSeverities(
            mapOf("roborazzi.problemSeverity.${declaredProblem.id}" to "disabled")
          )
        )
      )
    }
  }

  @Test
  fun formattedMessageTellsHowToChangeTheSeverity() {
    val errorMessage =
      RoborazziProblems.formatMessage(problem, "Something is off.", RoborazziProblemSeverity.Error)
    assertTrue(errorMessage.startsWith("Something is off."))
    assertTrue(errorMessage, errorMessage.contains("$key=warning"))

    val warningMessage =
      RoborazziProblems.formatMessage(problem, "Something is off.", RoborazziProblemSeverity.Warning)
    assertTrue(warningMessage, warningMessage.contains("$key=disabled"))

    val disabledMessage =
      RoborazziProblems.formatMessage(problem, "Something is off.", RoborazziProblemSeverity.Disabled)
    assertTrue(disabledMessage, disabledMessage.contains("$key=warning"))
  }

  @Test
  fun errorThrowsWithHowToChangeIt() {
    val error = assertThrows(IllegalStateException::class.java) {
      RoborazziProblems.report(problem, "Something is off.", RoborazziProblemSeverity.Error)
    }
    assertTrue(error.message!!.startsWith("Something is off."))
    assertTrue(error.message!!.contains("$key=warning"))
  }

  @Test
  fun warningLogsWithHowToChangeIt() {
    val log = captureStderr {
      RoborazziProblems.report(problem, "Something is off.", RoborazziProblemSeverity.Warning)
    }
    assertTrue(log, log.contains("Warning: Something is off."))
    assertTrue(log, log.contains("$key=disabled"))
  }

  @Test
  fun disabledReportsNothing() {
    val log = captureStderr {
      RoborazziProblems.report(problem, "Something is off.", RoborazziProblemSeverity.Disabled)
    }
    assertEquals("", log)
  }

  private fun withSystemProperties(vararg properties: Pair<String, String>, block: () -> Unit) {
    val originals = properties.map { (name, _) -> name to System.getProperty(name) }
    properties.forEach { (name, value) -> System.setProperty(name, value) }
    try {
      block()
    } finally {
      originals.forEach { (name, original) ->
        if (original == null) System.clearProperty(name) else System.setProperty(name, original)
      }
    }
  }

  private fun captureStderr(block: () -> Unit): String {
    val original = System.err
    val buffer = ByteArrayOutputStream()
    System.setErr(PrintStream(buffer, true))
    try {
      block()
    } finally {
      System.setErr(original)
    }
    return buffer.toString()
  }
}
