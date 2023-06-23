package com.github.takahirom.roborazzi

import java.io.File
import org.junit.runner.Description

@InternalRoborazziApi
object RoborazziContext {
  private var runnerOverrideOutputDirectory: String? = null
  private var ruleOverrideOutputDirectory: String? = null
  private var runnerOverrideRoborazziOptions: RoborazziOptions? = null
  private var ruleOverrideRoborazziOptions: RoborazziOptions? = null
  private var runnerOverrideFileProvider: FileProvider? = null
  private var ruleOverrideFileProvider: FileProvider? = null
  private var runnerOverrideDescription: Description? = null
  private var ruleOverrideDescription: Description? = null

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

  fun setRunnerOverrideFileCreator(fileProvider: FileProvider) {
    runnerOverrideFileProvider = fileProvider
  }

  fun clearRunnerOverrideFileCreator() {
    runnerOverrideFileProvider = null
  }

  fun setRuleOverrideFileProvider(fileProvider: FileProvider) {
    ruleOverrideFileProvider = fileProvider
  }

  fun clearRuleOverrideFileProvider() {
    ruleOverrideFileProvider = null
  }

  fun setRunnerOverrideDescription(description: Description) {
    runnerOverrideDescription = description
  }

  fun clearRunnerOverrideDescription() {
    runnerOverrideDescription = null
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

  // If we don't use Runner and JUnit Rule, we can't use this property.
  val fileProvider: ((Description, File, String) -> File)?
    get() = if (ruleOverrideFileProvider != null) {
      ruleOverrideFileProvider
    } else {
      runnerOverrideFileProvider
    }

  // If we don't use Runner and JUnit Rule, we can't use this property.
  val description: Description?
    get() = if (ruleOverrideDescription != null) {
      ruleOverrideDescription
    } else {
      runnerOverrideDescription
    }
}

@InternalRoborazziApi
fun provideRoborazziContext() = RoborazziContext