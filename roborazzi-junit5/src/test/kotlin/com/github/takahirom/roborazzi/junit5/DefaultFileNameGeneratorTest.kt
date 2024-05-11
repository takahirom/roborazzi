package com.github.takahirom.roborazzi.junit5

import com.github.takahirom.roborazzi.DefaultFileNameGenerator
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicContainer.dynamicContainer
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.RepetitionInfo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

abstract class DefaultFileNameGeneratorTest {
  protected fun runTest(expectedSuffix: String) {
    val filePath = DefaultFileNameGenerator.generateFilePath("png")
    val expectedFileName = "${javaClass.name}.$expectedSuffix"

    require(filePath.endsWith(expectedFileName)) {
      "Expected generated file name to be '$expectedFileName', but actual file path was: $filePath"
    }
  }
}

class DefaultFileNameGeneratorTestWithJUnit4 : DefaultFileNameGeneratorTest() {
  @org.junit.Test
  fun test() {
    runTest("test.png")
  }
}

class DefaultFileNameGeneratorTestWithJUnit5 : DefaultFileNameGeneratorTest() {
  @Test
  fun test() {
    runTest("test.png")
  }

  @ParameterizedTest
  @ValueSource(strings = ["A", "B"])
  fun parameterizedTest(value: String) {
    if (value == "A") {
      runTest("parameterizedTest.png")
    } else {
      runTest("parameterizedTest_2.png")
    }
  }

  @RepeatedTest(3)
  fun repeatedTest(info: RepetitionInfo) {
    when (info.currentRepetition) {
      1 -> runTest("repeatedTest.png")
      2 -> runTest("repeatedTest_2.png")
      3 -> runTest("repeatedTest_3.png")
    }
  }

  @TestFactory
  fun testFactory(): DynamicContainer = dynamicContainer(
    "testFactory",
    listOf(
      dynamicTest("first test") { runTest("testFactory.png") },
      dynamicTest("second test") { runTest("testFactory_2.png") },
    )
  )

  @CustomTest
  fun customTest(firstExecution: Boolean) {
    if (firstExecution) {
      runTest("customTest.png")
    } else {
      runTest("customTest_2.png")
    }
  }
}
