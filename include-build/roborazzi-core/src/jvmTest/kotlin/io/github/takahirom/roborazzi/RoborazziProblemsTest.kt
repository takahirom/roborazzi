package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziProblemSeverity
import com.github.takahirom.roborazzi.RoborazziProblems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(InternalRoborazziApi::class)
class RoborazziProblemsTest {
  private val problem = RoborazziProblems.ComposePreviewRenderScaleMismatch

  @Test
  fun emptyValueKeepsDefaults() {
    assertEquals(emptyMap<Any, Any>(), RoborazziProblems.parseSeverities(null))
    assertEquals(emptyMap<Any, Any>(), RoborazziProblems.parseSeverities(" "))
    assertEquals(RoborazziProblemSeverity.Error, RoborazziProblems.severityOf(problem, emptyMap()))
  }

  @Test
  fun configuredSeverityOverridesDefault() {
    val configured = RoborazziProblems.parseSeverities(" composePreview.renderScaleMismatch : warning ")
    assertEquals(RoborazziProblemSeverity.Warning, RoborazziProblems.severityOf(problem, configured))
  }

  @Test
  fun invalidValuesFail() {
    listOf(
      "composePreview.renderScaleMismatch",
      "composePreview.renderScaleMismatch:",
      "composePreview.renderScaleMismatch:warning,",
      "unknown.problem:warning",
      "composePreview.renderScaleMismatch:WARNING",
      "composePreview.renderScaleMismatch:warning,composePreview.renderScaleMismatch:disabled",
    ).forEach { value ->
      assertThrows(value, IllegalArgumentException::class.java) {
        RoborazziProblems.parseSeverities(value)
      }
    }
  }

  @Test
  fun errorThrowsWithHowToChangeIt() {
    val error = assertThrows(IllegalStateException::class.java) {
      RoborazziProblems.report(problem, "Something is off.", RoborazziProblemSeverity.Error)
    }
    assertTrue(error.message!!.startsWith("Something is off."))
    assertTrue(
      error.message!!.contains("roborazzi.problemSeverity=composePreview.renderScaleMismatch:warning")
    )
  }

  @Test
  fun warningAndDisabledDoNotThrow() {
    RoborazziProblems.report(problem, "Something is off.", RoborazziProblemSeverity.Warning)
    RoborazziProblems.report(problem, "Something is off.", RoborazziProblemSeverity.Disabled)
  }
}
