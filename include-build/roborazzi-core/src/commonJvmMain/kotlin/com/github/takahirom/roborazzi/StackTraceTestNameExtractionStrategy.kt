package com.github.takahirom.roborazzi

import org.junit.Test

/**
 * Default strategy for finding a suitable output name for [DefaultFileNameGenerator].
 * This implementation looks up the test class and method from the current stack trace.
 */
internal object StackTraceTestNameExtractionStrategy : TestNameExtractionStrategy {
  override fun extract(): Pair<String, String>? {
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
          method.getAnnotation(Test::class.java) != null
        } catch (e: NoClassDefFoundError) {
          false
        } catch (e: Exception) {
          false
        }
      }

    return stackTraceElement?.let {
      it.className to it.methodName
    }
  }
}
