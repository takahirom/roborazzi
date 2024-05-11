package com.github.takahirom.roborazzi.junit5

import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.parallel.ExecutionMode
import java.lang.reflect.Method
import java.util.Optional

/**
 * A JUnit 5 extension to track the currently executed test methods in a static reference.
 * This allows the [JUnit5TestNameExtractionStrategy] to extract the correct file name
 * for the default image capture when called from a JUnit 5 test method.
 */
class RoborazziExtension : BeforeEachCallback, AfterEachCallback {

  override fun beforeEach(context: ExtensionContext) {
    val isConcurrent = requireNotNull(context.executionMode) == ExecutionMode.CONCURRENT
    CurrentTestInfo.set(TestInfoImpl(context), isConcurrent)
  }

  override fun afterEach(context: ExtensionContext) {
    val isConcurrent = requireNotNull(context.executionMode) == ExecutionMode.CONCURRENT
    CurrentTestInfo.set(null, isConcurrent)
  }

  private class TestInfoImpl(context: ExtensionContext) : TestInfo {
    private val displayName = context.displayName
    private val tags = context.tags
    private val testClass = context.testClass
    private val testMethod = context.testMethod

    override fun getDisplayName(): String {
      return displayName
    }

    override fun getTags(): MutableSet<String> {
      return tags
    }

    override fun getTestClass(): Optional<Class<*>> {
      return testClass
    }

    override fun getTestMethod(): Optional<Method> {
      return testMethod
    }

    override fun toString(): String {
      return testMethod.toString()
    }
  }
}
