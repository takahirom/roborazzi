package com.github.takahirom.roborazzi.junit5

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * A JUnit 5 extension to track the currently executed test methods in a static reference.
 * This allows the [JUnit5TestNameExtractionStrategy] to extract the correct file name
 * for the default image capture when called from a JUnit 5 test method.
 */
class RoborazziExtension : BeforeEachCallback, AfterEachCallback {

  override fun beforeEach(context: ExtensionContext) {
    val info = TestInfoImpl(
      displayName = context.displayName,
      tags = context.tags,
      testClass = context.testClass,
      testMethod = context.testMethod,
    )

    val isConcurrent = requireNotNull(context.executionMode) == ExecutionMode.CONCURRENT
    CurrentTestInfo.set(info, isConcurrent)
  }

  override fun afterEach(context: ExtensionContext) {
    val isConcurrent = requireNotNull(context.executionMode) == ExecutionMode.CONCURRENT
    CurrentTestInfo.set(null, isConcurrent)
  }
}
