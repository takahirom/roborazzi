package com.github.takahirom.roborazzi

import java.io.File
import org.junit.runner.Description

@ExperimentalRoborazziApi
object RoborazziContext {
  private var runnerOverrideOutputDirectory: String? = null
  private var ruleOverrideOutputDirectory: String? = null
  private var runnerOverrideRoborazziOptions: RoborazziOptions? = null
  private var ruleOverrideRoborazziOptions: RoborazziOptions? = null
  private var runnerOverrideFileProvider: FileProvider? = null
  private var ruleOverrideFileProvider: FileProvider? = null
  private var runnerOverrideDescription: Description? = null
  private var ruleOverrideDescription: Description? = null

  @ExperimentalRoborazziApi
  fun setRunnerOverrideOutputDirectory(outputDirectory: String) {
    runnerOverrideOutputDirectory = outputDirectory
  }

  @ExperimentalRoborazziApi
  fun clearRunnerOverrideOutputDirectory() {
    runnerOverrideOutputDirectory = null
  }

  @InternalRoborazziApi
  fun setRuleOverrideOutputDirectory(outputDirectory: String) {
    ruleOverrideOutputDirectory = outputDirectory
  }

  @InternalRoborazziApi
  fun clearRuleOverrideOutputDirectory() {
    ruleOverrideOutputDirectory = null
  }

  @ExperimentalRoborazziApi
  fun setRunnerOverrideRoborazziOptions(options: RoborazziOptions) {
    runnerOverrideRoborazziOptions = options
  }

  @ExperimentalRoborazziApi
  fun clearRunnerOverrideRoborazziOptions() {
    runnerOverrideRoborazziOptions = null
  }

  @InternalRoborazziApi
  fun setRuleOverrideRoborazziOptions(options: RoborazziOptions) {
    ruleOverrideRoborazziOptions = options
  }

  @InternalRoborazziApi
  fun clearRuleOverrideRoborazziOptions() {
    ruleOverrideRoborazziOptions = null
  }

  @ExperimentalRoborazziApi
  fun setRunnerOverrideFileCreator(fileProvider: FileProvider) {
    runnerOverrideFileProvider = fileProvider
  }

  @ExperimentalRoborazziApi
  fun clearRunnerOverrideFileCreator() {
    runnerOverrideFileProvider = null
  }

  @InternalRoborazziApi
  fun setRuleOverrideFileProvider(fileProvider: FileProvider) {
    ruleOverrideFileProvider = fileProvider
  }

  @InternalRoborazziApi
  fun clearRuleOverrideFileProvider() {
    ruleOverrideFileProvider = null
  }

  @ExperimentalRoborazziApi
  fun setRunnerOverrideDescription(description: Description) {
    runnerOverrideDescription = description
  }

  @ExperimentalRoborazziApi
  fun clearRunnerOverrideDescription() {
    runnerOverrideDescription = null
  }

  @InternalRoborazziApi
  val outputDirectory
    get() = if (ruleOverrideOutputDirectory != null) {
      ruleOverrideOutputDirectory
    } else {
      runnerOverrideOutputDirectory
    } ?: DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH

  @InternalRoborazziApi
  val options
    get() = if (ruleOverrideRoborazziOptions != null) {
      ruleOverrideRoborazziOptions
    } else {
      runnerOverrideRoborazziOptions
    } ?: RoborazziOptions()

  // If we don't use Runner and JUnit Rule, we can't use this property.
  @InternalRoborazziApi
  val fileProvider: ((Description, File, String) -> File)?
    get() = if (ruleOverrideFileProvider != null) {
      ruleOverrideFileProvider
    } else {
      runnerOverrideFileProvider
    }

  // If we don't use Runner and JUnit Rule, we can't use this property.
  @InternalRoborazziApi
  val description: Description?
    get() = if (ruleOverrideDescription != null) {
      ruleOverrideDescription
    } else {
      runnerOverrideDescription
    }
}

@ExperimentalRoborazziApi
fun provideRoborazziContext() = RoborazziContext