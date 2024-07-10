package com.github.takahirom.roborazzi

import org.junit.Test
import org.junit.runner.Description
import java.io.File


object DefaultFileNameGenerator {
  enum class DefaultNamingStrategy(val optionName: String) {
    TestPackageAndClassAndMethod("testPackageAndClassAndMethod"),
    EscapedTestPackageAndClassAndMethod("escapedTestPackageAndClassAndMethod"),
    TestClassAndMethod("testClassAndMethod");

    @ExperimentalRoborazziApi
    fun generateOutputName(className: String, methodName: String?): String {
      return when (this) {
        TestPackageAndClassAndMethod -> "$className.$methodName"
        EscapedTestPackageAndClassAndMethod -> className.replace(
          ".",
          "_"
        ) + "." + methodName

        TestClassAndMethod -> className.substringAfterLast(".") + "." + methodName
      }
    }

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
        "$dir/${generateCountableOutputNameWithStacktrace()}.$extension"
      }

      RoborazziRecordFilePathStrategy.RelativePathFromRoborazziContextOutputDirectory -> {
        // The directory is specified by [fileWithRecordFilePathStrategy(filePath)]
        "${generateCountableOutputNameWithStacktrace()}.$extension"
      }
    }
  }

  val jupiterTestAnnotationOrNull = try {
    Class.forName("org.junit.jupiter.api.Test") as Class<Annotation>
  } catch (e: ClassNotFoundException) {
    null
  }

  private fun generateCountableOutputNameWithStacktrace(): String {
    val testName =
      generateOutputNameWithStackTrace()

    return countableOutputName(testName)
  }

  internal fun generateOutputNameWithStackTrace(): String {
    // Find test method name
    val allStackTraces = Thread.getAllStackTraces()
    val filteredTracces = allStackTraces
      // The Thread Name is come from here
      // https://github.com/robolectric/robolectric/blob/40832ada4a0651ecbb0151ebed2c99e9d1d71032/robolectric/src/main/java/org/robolectric/internal/AndroidSandbox.java#L67
      .filterKeys {
        it.name.contains("Main Thread")
          || it.name.contains("Test worker")
      }
    val traceElements = filteredTracces
      .flatMap { it.value.toList() }
    val stackTraceElement = traceElements
      .firstOrNull {
        try {
          val method = Class.forName(it.className).getMethod(it.methodName)
          method
            .getAnnotation(Test::class.java) != null ||
            (jupiterTestAnnotationOrNull != null && (method
              .getAnnotation(jupiterTestAnnotationOrNull) as? Annotation) != null)
        } catch (e: NoClassDefFoundError) {
          false
        } catch (e: Exception) {
          false
        }
      }
      ?: throw IllegalArgumentException("Roborazzi can't find method of test. Please specify file name or use Rule")
    val testName =
      defaultNamingStrategy.generateOutputName(stackTraceElement.className, stackTraceElement.methodName)
    return testName
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
    val testName = defaultNamingStrategy.generateOutputName(className, methodName)
    return testName
  }

}
