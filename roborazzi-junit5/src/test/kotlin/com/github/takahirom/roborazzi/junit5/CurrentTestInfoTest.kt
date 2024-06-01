package com.github.takahirom.roborazzi.junit5

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.RepetitionInfo
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.util.Optional

class CurrentTestInfoTest {
  private val methods = Methods::class.java.declaredMethods

  @Execution(ExecutionMode.CONCURRENT)
  @RepeatedTest(100)
  fun concurrentTest(repetitionInfo: RepetitionInfo) {
    val index = repetitionInfo.currentRepetition

    // Part 1: Apply a non-null info object
    doRoundTrip(
      TestInfoImpl(
        displayName = "Test $index",
        tags = mutableSetOf("test$index"),
        testClass = Optional.of(Methods::class.java),
        testMethod = Optional.of(methods[index % methods.size]),
      )
    ) { expected, actual ->
      assertEquals(expected?.displayName, actual?.displayName)
      assertEquals(expected?.tags, actual?.tags)
      assertEquals(expected?.testClass, actual?.testClass)
      assertEquals(expected?.testMethod, actual?.testMethod)
    }

    // Part 2: Clear this info object again
    doRoundTrip(null) { _, actual ->
      assertNull(actual)
    }
  }

  private fun doRoundTrip(
    expected: TestInfo?,
    assertionBlock: (TestInfo?, TestInfo?) -> Unit,
  ) {
    CurrentTestInfo.set(expected, isConcurrent = true)
    val actual = CurrentTestInfo.get()
    assertionBlock(expected, actual)
  }

  // Dummy class for this test, used to assign
  // random methods from each invocation
  // of the concurrent test declared above
  @Suppress("unused")
  private class Methods {
    fun test1() {}
    fun test2() {}
    fun test3() {}
    fun test4() {}
    fun test5() {}
    fun test6() {}
    fun test7() {}
    fun test8() {}
    fun test9() {}
    fun test10() {}
    fun test11() {}
    fun test12() {}
    fun test13() {}
    fun test14() {}
    fun test15() {}
    fun test16() {}
    fun test17() {}
    fun test18() {}
    fun test19() {}
  }
}
