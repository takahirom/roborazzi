package com.github.takahirom.roborazzi

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlin.test.Test
import kotlin.test.assertEquals
import platform.posix.getenv
import platform.posix.setenv
import platform.posix.unsetenv

@OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
class ContextDataPolicyTest {
  @OptIn(ExperimentalForeignApi::class)
  @Test
  fun contextDataIsDroppedWhenFlagDisabled() {
    val options = RoborazziOptions(contextData = mapOf("key" to "value"))
    withContextDataFlag("false") {
      assertEquals(emptyMap(), applyContextDataPolicy(options))
    }
  }

  @OptIn(ExperimentalForeignApi::class)
  @Test
  fun userSuppliedContextDataIsKeptWhenFlagEnabled() {
    val options = RoborazziOptions(contextData = mapOf("key" to "value"))
    withContextDataFlag("true") {
      // iOS keeps only the user-supplied context data (no JVM className default).
      assertEquals(mapOf("key" to "value"), applyContextDataPolicy(options))
    }
  }

  @OptIn(ExperimentalForeignApi::class)
  private inline fun withContextDataFlag(value: String, block: () -> Unit) {
    val key = "roborazzi.contextdata"
    val previous = getenv(key)?.toKString()
    setenv(key, value, 1)
    try {
      block()
    } finally {
      if (previous == null) {
        unsetenv(key)
      } else {
        setenv(key, previous, 1)
      }
    }
  }
}
