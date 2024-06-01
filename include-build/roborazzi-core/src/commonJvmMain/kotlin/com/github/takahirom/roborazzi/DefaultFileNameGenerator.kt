package com.github.takahirom.roborazzi

import org.junit.runner.Description
import java.io.File

object DefaultFileNameGenerator {
  enum class DefaultNamingStrategy(val optionName: String) {
    TestPackageAndClassAndMethod("testPackageAndClassAndMethod"),
    EscapedTestPackageAndClassAndMethod("escapedTestPackageAndClassAndMethod"),
    TestClassAndMethod("testClassAndMethod");

    companion object {
      fun fromOptionName(optionName: String): DefaultNamingStrategy {
        return values().firstOrNull { it.optionName == optionName } ?: TestPackageAndClassAndMethod
      }
    }
  }

  private val testNameToTakenCount = mutableMapOf<String, Int>()
  private val defaultNamingStrategy by lazy {
    roborazziDefaultNamingStrategy()
  }
  private val testNameExtractionStrategies by lazy {
    roborazziTestNameExtractionStrategies()
  }

  @InternalRoborazziApi
  fun generateFilePath(extension: String): String {
    val roborazziContext = provideRoborazziContext()
    val fileCreator = roborazziContext.fileProvider
    val description = roborazziContext.description
    if (fileCreator != null && description != null) {
      return fileCreator(
        description,
        File(roborazziContext.outputDirectory),
        extension
      ).absolutePath
    }
    return when (roborazziRecordFilePathStrategy()) {
      RoborazziRecordFilePathStrategy.RelativePathFromCurrentDirectory -> {
        val dir = roborazziContext.outputDirectory
        "$dir/${generateCountableOutputNameWithStrategies()}.$extension"
      }

      RoborazziRecordFilePathStrategy.RelativePathFromRoborazziContextOutputDirectory -> {
        // The directory is specified by [fileWithRecordFilePathStrategy(filePath)]
        "${generateCountableOutputNameWithStrategies()}.$extension"
      }
    }
  }

  private fun generateCountableOutputNameWithStrategies(): String {
    val testName =
      generateOutputNameWithStrategies()

    return countableOutputName(testName)
  }

  internal fun generateOutputNameWithStrategies(): String {
    for (strategy in testNameExtractionStrategies) {
      strategy.extract()?.let { (className, methodName) ->
        return generateOutputName(className, methodName)
      }
    }

    throw IllegalArgumentException("Roborazzi can't find method of test. Please specify file name or use Rule")
  }

  private fun countableOutputName(testName: String): String {
    val count = testNameToTakenCount.getOrPut(testName) { 1 }
    testNameToTakenCount[testName] = count + 1
    return if (count == 1) {
      testName
    } else {
      testName + "_$count"
    }
  }

  @ExperimentalRoborazziApi
  fun generateCountableOutputNameWithDescription(description: Description): String {
    val testName = generateOutputNameWithDescription(description)
    return countableOutputName(testName)
  }

  internal fun generateOutputNameWithDescription(description: Description): String {
    val className = description.className
    val methodName = description.methodName
    val testName = generateOutputName(className, methodName)
    return testName
  }

  private fun generateOutputName(className: String, methodName: String?): String {
    return when (defaultNamingStrategy) {
      DefaultNamingStrategy.TestPackageAndClassAndMethod -> "$className.$methodName"
      DefaultNamingStrategy.EscapedTestPackageAndClassAndMethod -> className.replace(
        ".",
        "_"
      ) + "." + methodName

      DefaultNamingStrategy.TestClassAndMethod -> className.substringAfterLast(".") + "." + methodName
    }
  }
}
