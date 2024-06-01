package com.github.takahirom.roborazzi

import org.junit.Test
import java.lang.reflect.Method

/**
 * Default strategy for finding a suitable output name for [DefaultFileNameGenerator].
 * This implementation looks up the test class and method from the current stack trace.
 */
internal object StackTraceTestNameExtractionStrategy : TestNameExtractionStrategy {
  override fun extract(): Pair<String, String>? {
    // Find test method name
    val allStackTraces = Thread.getAllStackTraces()
    val filteredTraces = allStackTraces
      // The Thread Name is come from here
      // https://github.com/robolectric/robolectric/blob/40832ada4a0651ecbb0151ebed2c99e9d1d71032/robolectric/src/main/java/org/robolectric/internal/AndroidSandbox.java#L67
      .filterKeys {
        it.name.contains("Main Thread")
          || it.name.contains("Test worker")
      }
    val traceElements = filteredTraces
      .flatMap { it.value.toList() }
    val stackTraceElement = traceElements
      .firstOrNull {
        try {
          val method = Class.forName(it.className).getMethod(it.methodName)
          method.isJUnit4Test() || method.isJUnit5Test()
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

  private fun Method.isJUnit4Test(): Boolean {
    return getAnnotation(Test::class.java) != null
  }

  // This JUnit 5 check works for basic usage of kotlin.test.Test with JUnit 5
  // in basic Compose desktop and multiplatform applications. For more complex
  // support including dynamic tests, [JUnit5TestNameExtractionStrategy] is required
  @Suppress("UNCHECKED_CAST")
  private val jupiterTestAnnotationOrNull = try {
    Class.forName("org.junit.jupiter.api.Test") as Class<Annotation>
  } catch (e: ClassNotFoundException) {
    null
  }

  @Suppress("USELESS_CAST")
  private fun Method.isJUnit5Test(): Boolean {
    return (jupiterTestAnnotationOrNull != null &&
      (getAnnotation(jupiterTestAnnotationOrNull) as? Annotation) != null)
  }
}
