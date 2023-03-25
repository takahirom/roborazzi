package com.github.takahirom.roborazzi

import org.junit.Test
import org.junit.runner.Description


object DefaultFileNameCreator {
  private val descriptionToTakenCount = mutableMapOf<String, Int>()

  fun generateFilePath(extension: String): String {
    return "$DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/${generateName()}.$extension"
  }

  private fun generateName(): String {
    // Find test method name
    val allStackTraces = Thread.getAllStackTraces()
    val filteredTracces = allStackTraces
      .filterKeys { it.name.contains("Main") }
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
    val count = descriptionToTakenCount.getOrPut(testName) { 1 }
    descriptionToTakenCount[testName] = count + 1
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