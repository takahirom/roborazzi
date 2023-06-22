package com.github.takahirom.roborazzi

@InternalRoborazziApi
object RoborazziContext {
  private var runnerOverrideOutputDirectory: String? = null
  private var ruleOverrideOutputDirectory: String? = null
  private var runnerOverrideRoborazziOptions: RoborazziOptions? = null
  private var ruleOverrideRoborazziOptions: RoborazziOptions? = null

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

  fun setRunnerOverrideRoborazziOptions(options: RoborazziOptions) {
    runnerOverrideRoborazziOptions = options
  }

  fun clearRunnerOverrideRoborazziOptions() {
    runnerOverrideRoborazziOptions = null
  }

  fun setRuleOverrideRoborazziOptions(options: RoborazziOptions) {
    ruleOverrideRoborazziOptions = options
  }

  fun clearRuleOverrideRoborazziOptions() {
    ruleOverrideRoborazziOptions = null
  }

  val outputDirectory
    get() = if (ruleOverrideOutputDirectory != null) {
      ruleOverrideOutputDirectory
    } else {
      runnerOverrideOutputDirectory
    } ?: DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH

  val options
    get() = if (ruleOverrideRoborazziOptions != null) {
      ruleOverrideRoborazziOptions
    } else {
      runnerOverrideRoborazziOptions
    } ?: RoborazziOptions()
}

@InternalRoborazziApi
fun provideRoborazziContext() = RoborazziContext