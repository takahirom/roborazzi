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
 * Users change the severity of a problem with one Gradle property per problem,
 * `roborazzi.problemSeverity.<id>` (see [RoborazziProblems.severityPropertyName]).
 *
 * Choosing a new problem:
 * - [id] is `area.problem` in lowerCamelCase. The area is the feature as users see it in the
 *   Gradle DSL and docs (for example `composePreview` for `generateComposePreview*Tests`), not
 *   where the check runs. Avoid renaming or reusing a released ID: users list IDs in their
 *   Gradle properties, and an unknown ID fails the build.
 * - [defaultSeverity] is [RoborazziProblemSeverity.Error] when a feature the user turned on
 *   cannot work as configured. A new check on existing usage starts as
 *   [RoborazziProblemSeverity.Warning], so upgrading does not break builds. Don't lower the
 *   severity of a released error as a side effect of other work.
 * - Report a problem that defaults to [RoborazziProblemSeverity.Warning] from the Gradle plugin
 *   when you can. A warning reported in the test JVM goes to its standard error, which Gradle
 *   shows only with `--info` or `testLogging`, so users may never see it.
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
  const val SeverityPropertyPrefix: String = "roborazzi.problemSeverity."

  /** A custom preview tester did not carry the configured `renderScale` through to the capture. */
  val ComposePreviewRenderScaleMismatch: RoborazziProblem =
    RoborazziProblem("composePreview.renderScaleMismatch", RoborazziProblemSeverity.Error)

  /** Every problem, so that a typo in an ID is reported wherever the setting is read. */
  val all: List<RoborazziProblem> = listOf(
    ComposePreviewRenderScaleMismatch,
  )

  /** The Gradle property that sets the severity of [problem], such as `roborazzi.problemSeverity.<id>`. */
  fun severityPropertyName(problem: RoborazziProblem): String = "$SeverityPropertyPrefix${problem.id}"

  /**
   * Reads the severities set by `roborazzi.problemSeverity.<id>` properties in [properties], keyed
   * by problem ID. Properties that do not start with [SeverityPropertyPrefix] are ignored.
   *
   * Fails on an unknown ID or severity, so a typo cannot silently leave a problem at its default.
   * The Gradle plugin calls this with every `roborazzi.*` property, so the build fails before any
   * test runs.
   */
  fun parseSeverities(properties: Map<String, Any?>): Map<String, RoborazziProblemSeverity> {
    return properties
      .filterKeys { it.startsWith(SeverityPropertyPrefix) }
      .map { (name, value) ->
        val id = name.removePrefix(SeverityPropertyPrefix)
        val problem = requireNotNull(all.firstOrNull { it.id == id }) {
          "Unknown problem id '$id' in $name. Known ids: ${all.joinToString()}."
        }
        problem.id to parseSeverity(name, value)
      }
      .toMap()
  }

  /**
   * The severity of [problem] in [severities], the result of [parseSeverities]. Checks that run in
   * the Gradle plugin use this: `roborazzi.problemSeverity.<id>` Gradle properties are not system
   * properties there, so [severityFromSystemProperty] would silently return the default.
   */
  fun severityOf(
    problem: RoborazziProblem,
    severities: Map<String, RoborazziProblemSeverity>,
  ): RoborazziProblemSeverity = severities[problem.id] ?: problem.defaultSeverity

  /**
   * The severity of [problem] in the test JVM, read from its `roborazzi.problemSeverity.<id>`
   * system property, which the Gradle plugin forwards. Settings for other problems are not read,
   * so an ID that only a newer plugin knows cannot fail a test.
   */
  fun severityFromSystemProperty(problem: RoborazziProblem): RoborazziProblemSeverity {
    val name = severityPropertyName(problem)
    val value = getSystemProperty(name) ?: return problem.defaultSeverity
    return parseSeverity(name, value)
  }

  /** [message] followed by the ID of [problem] and how to change its [severity]. */
  fun formatMessage(
    problem: RoborazziProblem,
    message: String,
    severity: RoborazziProblemSeverity,
  ): String {
    val otherSeverity = when (severity) {
      RoborazziProblemSeverity.Error, RoborazziProblemSeverity.Disabled -> RoborazziProblemSeverity.Warning
      RoborazziProblemSeverity.Warning -> RoborazziProblemSeverity.Disabled
    }
    return "$message\n" +
      "\n" +
      "Problem: ${problem.id} (${severity.value}). To change its severity, set " +
      "${severityPropertyName(problem)}=${otherSeverity.value} in gradle.properties."
  }

  /**
   * Reports [problem] from the test JVM with the severity the user configured: throws
   * [IllegalStateException] for an error, logs a warning to standard error, or does nothing when
   * it is disabled. Checks in the Gradle plugin use [severityOf] and [formatMessage] and report
   * through Gradle instead.
   */
  fun report(
    problem: RoborazziProblem,
    message: String,
    severity: RoborazziProblemSeverity = severityFromSystemProperty(problem),
  ) {
    when (severity) {
      RoborazziProblemSeverity.Error ->
        throw IllegalStateException(formatMessage(problem, message, severity))
      RoborazziProblemSeverity.Warning ->
        roborazziErrorLog("Warning: ${formatMessage(problem, message, severity)}")
      RoborazziProblemSeverity.Disabled -> Unit
    }
  }

  private fun parseSeverity(name: String, value: Any?): RoborazziProblemSeverity {
    val trimmed = value?.toString()?.trim()
    return requireNotNull(RoborazziProblemSeverity.entries.firstOrNull { it.value == trimmed }) {
      "Unknown severity '$value' in $name. " +
        "Use one of: ${RoborazziProblemSeverity.entries.joinToString { it.value }}."
    }
  }

}
