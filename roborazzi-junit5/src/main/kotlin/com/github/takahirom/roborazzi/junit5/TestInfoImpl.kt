package com.github.takahirom.roborazzi.junit5

import org.junit.jupiter.api.TestInfo
import java.lang.reflect.Method
import java.util.Optional

internal class TestInfoImpl(
  private val displayName: String,
  private val tags: MutableSet<String>,
  private val testClass: Optional<Class<*>>,
  private val testMethod: Optional<Method>,
) : TestInfo {
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
