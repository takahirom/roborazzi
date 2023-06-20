package com.github.takahirom.roborazzi

@InternalRoborazziApi
object RoborazziContext {
  private var runnerOverrideOutputDirectory: String? = null
  private var ruleOverrideOutputDirectory: String? = null

  fun setRunnerOverrideOutputDirectory(outputDirectory: String) {
    runnerOverrideOutputDirectory = outputDirectory
  }

  fun clearRunnerOverrideOutputDirectory() {
    runnerOverrideOutputDirectory = null
  }

  fun setRuleOverrideOutputDirectory(outputDirectory: String) {
    ruleOverrideOutputDirectory = outputDirectory
  }

  fun clearRuleOverrideOutputDirectory() {
    ruleOverrideOutputDirectory = null
  }

  val outputDirectory
    get() = if (ruleOverrideOutputDirectory != null) {
      ruleOverrideOutputDirectory
    } else {
      runnerOverrideOutputDirectory
    } ?: DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH
}

@InternalRoborazziApi
fun provideRoborazziContext() = RoborazziContext