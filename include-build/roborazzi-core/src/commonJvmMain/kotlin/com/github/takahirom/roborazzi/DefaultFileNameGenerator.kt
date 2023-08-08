package com.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.roborazziDefaultNamingStrategy
import java.io.File
import org.junit.Test
import org.junit.runner.Description


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

  @InternalRoborazziApi
  fun generateFilePath(extension: String): String {
    val roborazziContext = provideRoborazziContext()
    val fileCreator = RoborazziContext.fileProvider
    val description = RoborazziContext.description
    if (fileCreator != null && description != null) {
      return fileCreator(
        description,
        File(RoborazziContext.outputDirectory),
        extension
      ).absolutePath
    }
    val dir = RoborazziContext.outputDirectory
    return "$dir/${generateName()}.$extension"
  }

  val jupiterTestAnnotationOrNull = try {
    Class.forName("org.junit.jupiter.api.Test") as Class<Annotation>
  } catch (e: ClassNotFoundException) {
    null
  }

  private fun generateName(): String {
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
      generateTestName(stackTraceElement.className, stackTraceElement.methodName)
    return countableTestName(testName)
  }

  private fun countableTestName(testName: String): String {
    val count = testNameToTakenCount.getOrPut(testName) { 1 }
    testNameToTakenCount[testName] = count + 1
    return if (count == 1) {
      testName
    } else {
      testName + "_$count"
    }
  }

  fun generate(description: Description): String {
    description.testClass
    val className = description.className
    val methodName = description.methodName
    val testName = generateTestName(className, methodName)
    return countableTestName(testName)
  }

  private fun generateTestName(className: String, methodName: String?): String {
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