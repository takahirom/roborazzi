package com.github.takahirom.roborazzi

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

@OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
class RuleReleaseTest {
  private inline fun <reified T> unusedProxy(): T = Proxy.newProxyInstance(
    T::class.java.classLoader, arrayOf(T::class.java)
  ) { _, method, _ -> error("Unexpected call: $method") } as T

  private val description = Description.createTestDescription(javaClass, "probe")
  private fun statement(block: () -> Unit) = object : Statement() {
    override fun evaluate() = block()
  }
  private class ExpectedFailure : RuntimeException()

  @Test fun sharesRuleThroughTeardownAndReleasesForNextExecution() {
    var created = 0
    val parameter = ComposePreviewTester.TestParameter.JUnit4TestParameter(
      { created++; unusedProxy<ComposeContentTestRule>() }, unusedProxy<ComposablePreview<Any>>()
    )
    repeat(2) {
      val rule = parameter.composeTestRule
      val lifecycle = TestRule { base, _ -> statement {
        assertSame(rule, parameter.composeTestRule)
        try { base.evaluate() } finally { assertSame(rule, parameter.composeTestRule) }
      } }
      parameter.releaseComposeTestRuleAfter { lifecycle }.apply(statement {
        assertSame(rule, parameter.composeTestRule)
      }, description).evaluate()
      assertEquals(it + 1, created)
    }
    assertEquals(2, created)
  }

  @Test fun releasesOnSetupFailure() = checkFailure("setup")
  @Test fun releasesOnTestFailure() = checkFailure("test")
  @Test fun releasesOnTeardownFailure() = checkFailure("teardown")
  @Test fun releasesOnApplyFailure() = checkFailure("apply")

  private fun checkFailure(stage: String) {
    val parameter = ComposePreviewTester.TestParameter.JUnit4TestParameter(
      { unusedProxy<ComposeContentTestRule>() }, unusedProxy<ComposablePreview<Any>>()
    )
    val first = parameter.composeTestRule
    val failure = ExpectedFailure()
    val lifecycle = TestRule { base, _ ->
      if (stage == "apply") throw failure
      statement {
        try {
          if (stage == "setup") throw failure
          base.evaluate()
        } finally {
          assertSame(first, parameter.composeTestRule)
          if (stage == "teardown") throw failure
        }
      }
    }
    try {
      parameter.releaseComposeTestRuleAfter { lifecycle }.apply(statement {
        assertSame(first, parameter.composeTestRule)
        if (stage == "test") throw failure
      }, description).evaluate()
      fail("Expected failure")
    } catch (actual: ExpectedFailure) { assertSame(failure, actual) }
    assertNotSame(first, parameter.composeTestRule)
  }

  @Test fun releasesOnFactoryFailure() {
    val parameter = ComposePreviewTester.TestParameter.JUnit4TestParameter(
      { unusedProxy<ComposeContentTestRule>() }, unusedProxy<ComposablePreview<Any>>()
    )
    val first = parameter.composeTestRule
    val failure = ExpectedFailure()
    try {
      parameter.releaseComposeTestRuleAfter { throw failure }
      fail("Expected failure")
    } catch (actual: ExpectedFailure) { assertSame(failure, actual) }
    assertNotSame(first, parameter.composeTestRule)
  }

  @Test fun releasesWhenCustomRuleSkipsBody() {
    val parameter = ComposePreviewTester.TestParameter.JUnit4TestParameter(
      { unusedProxy<ComposeContentTestRule>() }, unusedProxy<ComposablePreview<Any>>()
    )
    val first = parameter.composeTestRule
    parameter.releaseComposeTestRuleAfter { TestRule { _, _ -> statement {} } }
      .apply(statement { fail("Skipped body must not run") }, description).evaluate()
    assertNotSame(first, parameter.composeTestRule)
  }

  @Test fun unusedRuleIsNotCreatedByCleanup() {
    val parameter = ComposePreviewTester.TestParameter.JUnit4TestParameter(
      { error("Must remain lazy") }, unusedProxy<ComposablePreview<Any>>()
    )
    parameter.releaseComposeTestRuleAfter { TestRule { base, _ -> base } }
      .apply(statement {}, description).evaluate()
  }
}
