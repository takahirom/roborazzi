package com.github.takahirom.roborazzi.junit5

import org.junit.jupiter.api.TestInfo

/**
 * A shared, static data container for storing the currently executed test method.
 * This is updated by [RoborazziExtension] and read by [JUnit5TestNameExtractionStrategy]
 * from different class loaders, bridging the gap between test definition and their execution.
 */
internal object CurrentTestInfo {
  private val concurrentRef = ThreadLocal<TestInfo>()
  private var sameThreadRef: TestInfo? = null

  fun set(info: TestInfo?, isConcurrent: Boolean) {
    if (isConcurrent) {
      concurrentRef.set(info)
    } else {
      sameThreadRef = info
    }
  }

  fun get(): TestInfo? {
    return concurrentRef.get() ?: sameThreadRef
  }
}
