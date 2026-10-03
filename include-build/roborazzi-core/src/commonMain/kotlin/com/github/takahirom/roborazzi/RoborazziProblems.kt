package com.github.takahirom.roborazzi

/**
 * How a [RoborazziProblem] is reported: [Error] fails, [Warning] logs and continues, and
 * [Disabled] reports nothing.
 */
@InternalRoborazziApi
enum class RoborazziProblemSeverity(val value: String) {
  Error("error"),
  Warning("warning"),
  Disabled("disabled"),
}

/**
 * Something that is probably a mistake, but that a user may have done on purpose.
 *
 * Invalid input that Roborazzi cannot work with is not a problem: fail with `require` instead.
 * Users change the severity of a problem by [id] with [RoborazziProblems.SeverityProperty].
 *
 * Choosing a new problem:
 * - [id] is `area.problem` in lowerCamelCase. The area is the feature as users see it in the
 *   Gradle DSL and docs (for example `composePreview` for `generateComposePreview*Tests`), not
 *   where the check runs. Released IDs are never renamed or reused.
 * - [defaultSeverity] is [RoborazziProblemSeverity.Error] when a feature the user turned on
 *   cannot work as configured. A new check on existing usage starts as
 *   [RoborazziProblemSeverity.Warning], so upgrading does not break builds. Don't lower the
 *   severity of a released error as a side effect of other work.
 */
@InternalRoborazziApi
class RoborazziProblem internal constructor(
  val id: String,
  val defaultSeverity: RoborazziProblemSeverity,
) {
  override fun toString(): String = id
}

@InternalRoborazziApi
object RoborazziProblems {
  const val SeverityProperty: String = "roborazzi.problemSeverity"

  /** A custom preview tester did not carry the configured `renderScale` through to the capture. */
  val ComposePreviewRenderScaleMismatch: RoborazziProblem =
    RoborazziProblem("composePreview.renderScaleMismatch", RoborazziProblemSeverity.Error)

  /** Every problem, so that a typo in an ID is reported wherever the setting is read. */
  val all: List<RoborazziProblem> = listOf(
    ComposePreviewRenderScaleMismatch,
  )

  /**
   * Parses a [SeverityProperty] value such as `composePreview.renderScaleMismatch:warning`.
   *
   * Fails on an unknown ID or severity, or an ID listed twice, so a typo cannot silently leave a
   * problem at its default.
   */
  fun parseSeverities(value: String?): Map<RoborazziProblem, RoborazziProblemSeverity> {
    if (value.isNullOrBlank()) return emptyMap()
    val result = mutableMapOf<RoborazziProblem, RoborazziProblemSeverity>()
    value.split(",").map { it.trim() }.forEach { entry ->
      val parts = entry.split(":").map { it.trim() }
      require(parts.size == 2 && parts.none { it.isEmpty() }) {
        "Invalid $SeverityProperty entry '$entry' in '$value'. Use <problem id>:<severity>, " +
          "for example ${ComposePreviewRenderScaleMismatch.id}:warning."
      }
      val (id, severityValue) = parts
      val problem = requireNotNull(all.firstOrNull { it.id == id }) {
        "Unknown problem id '$id' in $SeverityProperty. Known ids: ${all.joinToString()}."
      }
      val severity = requireNotNull(RoborazziProblemSeverity.entries.firstOrNull { it.value == severityValue }) {
        "Unknown severity '$severityValue' for '$id' in $SeverityProperty. " +
          "Use one of: ${RoborazziProblemSeverity.entries.joinToString { it.value }}."
      }
      require(problem !in result) {
        "Problem id '$id' is listed more than once in $SeverityProperty."
      }
      result[problem] = severity
    }
    return result
  }

  fun severityOf(
    problem: RoborazziProblem,
    configured: Map<RoborazziProblem, RoborazziProblemSeverity> =
      parseSeverities(getSystemProperty(SeverityProperty)),
  ): RoborazziProblemSeverity = configured[problem] ?: problem.defaultSeverity

  /**
   * Reports [problem] with the severity the user configured: throws [IllegalStateException] for
   * an error, logs a warning, or does nothing when it is disabled.
   */
  fun report(
    problem: RoborazziProblem,
    message: String,
    severity: RoborazziProblemSeverity = severityOf(problem),
  ) {
    val otherSeverity = when (severity) {
      RoborazziProblemSeverity.Error -> RoborazziProblemSeverity.Warning
      RoborazziProblemSeverity.Warning, RoborazziProblemSeverity.Disabled -> RoborazziProblemSeverity.Disabled
    }
    val fullMessage = "$message\n" +
      "\n" +
      "Problem: ${problem.id} (${severity.value}). To change its severity, set " +
      "$SeverityProperty=${problem.id}:${otherSeverity.value} in gradle.properties."
    when (severity) {
      RoborazziProblemSeverity.Error -> throw IllegalStateException(fullMessage)
      RoborazziProblemSeverity.Warning -> roborazziErrorLog("Warning: $fullMessage")
      RoborazziProblemSeverity.Disabled -> Unit
    }
  }
}
