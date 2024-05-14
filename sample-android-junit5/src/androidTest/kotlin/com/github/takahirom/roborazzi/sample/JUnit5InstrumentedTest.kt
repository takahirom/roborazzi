package com.github.takahirom.roborazzi.sample

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JUnit5InstrumentedTest {
  @Test
  fun useAppContext() {
    // The basic example instrumentation test, but with JUnit 5
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    assertEquals("com.github.takahirom.roborazzi.sample.test", appContext.packageName)
  }
}
