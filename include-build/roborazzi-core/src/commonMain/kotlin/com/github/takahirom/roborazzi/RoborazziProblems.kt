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
  const val SeverityProperty: String = "roborazzi.problemSeverity"

  /** A custom preview tester did not carry the configured `renderScale` through to the capture. */
  val ComposePreviewRenderScaleMismatch: RoborazziProblem =
    RoborazziProblem("composePreview.renderScaleMismatch", RoborazziProblemSeverity.Error)

  /** Every problem, so that a typo in an ID is reported wherever the setting is read. */
  val all: List<RoborazziProblem> = listOf(
    ComposePreviewRenderScaleMismatch,
  )

  /** The Gradle property that sets the severity of [problem], such as `roborazzi.problemSeverity.<id>`. */
  fun severityPropertyName(problem: RoborazziProblem): String = "$SeverityProperty.${problem.id}"

  /**
   * Reads the severities set by `roborazzi.problemSeverity.<id>` properties in [properties], keyed
   * by problem ID. Properties that do not start with [SeverityProperty] are ignored.
   *
   * Fails on an unknown ID or severity, so a typo cannot silently leave a problem at its default.
   * The Gradle plugin calls this with every `roborazzi.*` property, so the build fails before any
   * test runs.
   */
  fun parseSeverities(properties: Map<String, Any?>): Map<String, RoborazziProblemSeverity> {
    properties[SeverityProperty]?.let { value ->
      throw IllegalArgumentException(
        "$SeverityProperty=$value is not supported. Set one property per problem instead, " +
          "for example:\n" + suggestKeyPerProblem(value.toString())
      )
    }
    val prefix = "$SeverityProperty."
    return properties
      .filterKeys { it.startsWith(prefix) }
      .map { (name, value) ->
        val id = name.removePrefix(prefix)
        val problem = requireNotNull(all.firstOrNull { it.id == id }) {
          "Unknown problem id '$id' in $name. Known ids: ${all.joinToString()}."
        }
        problem.id to parseSeverity(name, value)
      }
      .toMap()
  }

  /**
   * The severity of [problem]. By default, it is read from the `roborazzi.problemSeverity.<id>`
   * system property of [problem] only, which the Gradle plugin forwards to the test JVM. Settings
   * for other problems are not read, so an ID that only a newer plugin knows cannot fail a test.
   */
  fun severityOf(
    problem: RoborazziProblem,
    severities: Map<String, RoborazziProblemSeverity> = systemPropertySeverity(problem),
  ): RoborazziProblemSeverity = severities[problem.id] ?: problem.defaultSeverity

  /** [message] followed by the ID of [problem] and how to change its [severity]. */
  fun message(
    problem: RoborazziProblem,
    message: String,
    severity: RoborazziProblemSeverity,
  ): String {
    val otherSeverity = when (severity) {
      RoborazziProblemSeverity.Error -> RoborazziProblemSeverity.Warning
      RoborazziProblemSeverity.Warning, RoborazziProblemSeverity.Disabled -> RoborazziProblemSeverity.Disabled
    }
    return "$message\n" +
      "\n" +
      "Problem: ${problem.id} (${severity.value}). To change its severity, set " +
      "${severityPropertyName(problem)}=${otherSeverity.value} in gradle.properties."
  }

  /**
   * Reports [problem] with the severity the user configured: throws [IllegalStateException] for
   * an error, logs a warning to standard error, or does nothing when it is disabled.
   */
  fun report(
    problem: RoborazziProblem,
    message: String,
    severity: RoborazziProblemSeverity = severityOf(problem),
  ) {
    when (severity) {
      RoborazziProblemSeverity.Error ->
        throw IllegalStateException(message(problem, message, severity))
      RoborazziProblemSeverity.Warning ->
        roborazziErrorLog("Warning: ${message(problem, message, severity)}")
      RoborazziProblemSeverity.Disabled -> Unit
    }
  }

  private fun systemPropertySeverity(
    problem: RoborazziProblem,
  ): Map<String, RoborazziProblemSeverity> {
    val name = severityPropertyName(problem)
    val value = getSystemProperty(name) ?: return emptyMap()
    return mapOf(problem.id to parseSeverity(name, value))
  }

  private fun parseSeverity(name: String, value: Any?): RoborazziProblemSeverity {
    val trimmed = value?.toString()?.trim()
    return requireNotNull(RoborazziProblemSeverity.entries.firstOrNull { it.value == trimmed }) {
      "Unknown severity '$value' in $name. " +
        "Use one of: ${RoborazziProblemSeverity.entries.joinToString { it.value }}."
    }
  }

  private fun suggestKeyPerProblem(value: String): String {
    val lines = value.split(",").mapNotNull { entry ->
      val parts = entry.split(":").map { it.trim() }
      if (parts.size == 2 && parts.none { it.isEmpty() }) {
        "$SeverityProperty.${parts[0]}=${parts[1]}"
      } else {
        null
      }
    }
    return lines.ifEmpty {
      listOf("${severityPropertyName(ComposePreviewRenderScaleMismatch)}=warning")
    }.joinToString("\n")
  }
}
