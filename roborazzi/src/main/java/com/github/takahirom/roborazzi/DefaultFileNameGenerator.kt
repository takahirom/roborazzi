package com.github.takahirom.roborazzi

import org.junit.Test
import org.junit.runner.Description


object DefaultFileNameGenerator {
  private val testNameToTakenCount = mutableMapOf<String, Int>()

  fun generateFilePath(extension: String): String {
    return "$DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/${generateName()}.$extension"
  }

  private fun generateName(): String {
    // Find test method name
    val allStackTraces = Thread.getAllStackTraces()
    val filteredTracces = allStackTraces
      // The Thread Name is come from here
      // https://github.com/robolectric/robolectric/blob/40832ada4a0651ecbb0151ebed2c99e9d1d71032/robolectric/src/main/java/org/robolectric/internal/AndroidSandbox.java#L67
      .filterKeys { it.name.contains("Main Thread") }
    val traceElements = filteredTracces
      .flatMap { it.value.toList() }
    val stackTraceElement = traceElements
      .firstOrNull {
        try {
          Class.forName(it.className).getMethod(it.methodName)
            .getAnnotation(Test::class.java) != null
        } catch (e: Exception) {
          false
        }
      }
      ?: throw IllegalArgumentException("Roborazzi can't find method of test. Please specify file name or use Rule")
    val testName =
      (stackTraceElement.className + "." + stackTraceElement.methodName).replace(".", "_")
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
    val methodName = description.className.replace(".", "_") + "_" + description.methodName
    return countableTestName(methodName)
  }

}