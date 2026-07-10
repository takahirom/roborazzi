package com.github.takahirom.roborazzi

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.IdlingResource
import androidx.compose.ui.test.MainTestClock
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Boundary tests for [RoborazziComposeTestRuleOption.createScenario]:
 * - a rule without `getActivityRule()` falls back to the default scenario creation
 * - a rule whose `getActivityRule()` exists but yields no usable scenario fails loudly
 * - an explicitly provided scenario is used verbatim, lazily, without reflection
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
class RoborazziComposeTestRuleOptionTest {

  @Test
  fun whenRuleHasNoActivityRuleCreateScenarioFallsBackToChain() {
    launchScenario { scenario ->
      val option = RoborazziComposeTestRuleOption(NoActivityRuleComposeRule())
      var chainCalled = false
      val result = option.createScenario {
        chainCalled = true
        scenario
      }
      assertTrue("chain() should be used as the designed fallback", chainCalled)
      assertSame(scenario, result)
    }
  }

  @Test
  fun whenActivityRuleHasNoScenarioCreateScenarioThrows() {
    val option = RoborazziComposeTestRuleOption(BrokenActivityRuleComposeRule())
    assertThrowsScenarioExtractionError {
      option.createScenario { error("chain() should not be called") }
    }
  }

  @Test
  fun whenScenarioIsNotAnActivityScenarioCreateScenarioThrows() {
    val option = RoborazziComposeTestRuleOption(NonScenarioActivityRuleComposeRule())
    assertThrowsScenarioExtractionError {
      option.createScenario { error("chain() should not be called") }
    }
  }

  @Test
  fun explicitProviderIsUsedVerbatimAndLazilyWithoutReflection() {
    launchScenario { scenario ->
      var providerCalls = 0
      // ThrowingActivityRuleComposeRule proves reflection is skipped: touching its
      // getActivityRule() would throw.
      val option = RoborazziComposeTestRuleOption(ThrowingActivityRuleComposeRule()) {
        providerCalls++
        scenario
      }
      assertEquals("provider must not be invoked at construction time", 0, providerCalls)
      val result = option.createScenario { error("chain() should not be called") }
      assertEquals(1, providerCalls)
      assertSame(scenario, result)
    }
  }

  private fun assertThrowsScenarioExtractionError(block: () -> Unit) {
    try {
      block()
      fail("Expected IllegalStateException")
    } catch (e: IllegalStateException) {
      val message = e.message.orEmpty()
      assertTrue(
        "message should name the direct escape hatch: $message",
        message.contains("RoborazziComposeOptions.Builder.composeTestRule(composeTestRule) { activityScenario }")
      )
      assertTrue(
        "message should name the generated-test escape hatch: $message",
        message.contains("JUnit4TestLifecycleOptions.activityScenarioProvider")
      )
    }
  }

  private fun launchScenario(block: (ActivityScenario<out ComponentActivity>) -> Unit) {
    registerRoborazziActivityToRobolectricIfNeeded()
    ActivityScenario.launch(RoborazziActivity::class.java).use(block)
  }
}

// The fakes are top-level (not private nested classes) so that the production code's
// reflective Method.invoke can access their public methods without access errors.
open class NoActivityRuleComposeRule : ComposeTestRule {
  override val density: Density get() = throw UnsupportedOperationException()
  override val mainClock: MainTestClock get() = throw UnsupportedOperationException()
  override fun <T> runOnUiThread(action: () -> T): T = throw UnsupportedOperationException()
  override fun <T> runOnIdle(action: () -> T): T = throw UnsupportedOperationException()
  override fun waitForIdle(): Unit = throw UnsupportedOperationException()
  override suspend fun awaitIdle(): Unit = throw UnsupportedOperationException()
  override fun waitUntil(timeoutMillis: Long, condition: () -> Boolean): Unit =
    throw UnsupportedOperationException()

  @ExperimentalTestApi
  override fun waitUntilAtLeastOneExists(matcher: SemanticsMatcher, timeoutMillis: Long): Unit =
    throw UnsupportedOperationException()

  @ExperimentalTestApi
  override fun waitUntilDoesNotExist(matcher: SemanticsMatcher, timeoutMillis: Long): Unit =
    throw UnsupportedOperationException()

  @ExperimentalTestApi
  override fun waitUntilExactlyOneExists(matcher: SemanticsMatcher, timeoutMillis: Long): Unit =
    throw UnsupportedOperationException()

  @ExperimentalTestApi
  override fun waitUntilNodeCount(matcher: SemanticsMatcher, count: Int, timeoutMillis: Long): Unit =
    throw UnsupportedOperationException()

  override fun onNode(
    matcher: SemanticsMatcher,
    useUnmergedTree: Boolean
  ): SemanticsNodeInteraction = throw UnsupportedOperationException()

  override fun onAllNodes(
    matcher: SemanticsMatcher,
    useUnmergedTree: Boolean
  ): SemanticsNodeInteractionCollection = throw UnsupportedOperationException()

  override fun registerIdlingResource(idlingResource: IdlingResource): Unit =
    throw UnsupportedOperationException()

  override fun unregisterIdlingResource(idlingResource: IdlingResource): Unit =
    throw UnsupportedOperationException()

  override fun apply(base: org.junit.runners.model.Statement, description: Description) = base
}

// Exposes getActivityRule() like AndroidComposeTestRule, but the returned rule
// has no getScenario() method.
class BrokenActivityRuleComposeRule : NoActivityRuleComposeRule() {
  @Suppress("unused")
  fun getActivityRule(): Any = Any()
}

class NonScenarioActivityRuleComposeRule : NoActivityRuleComposeRule() {
  @Suppress("unused")
  fun getActivityRule(): Any = NotAScenarioRule()

  class NotAScenarioRule {
    @Suppress("unused")
    fun getScenario(): Any = "not an ActivityScenario"
  }
}

class ThrowingActivityRuleComposeRule : NoActivityRuleComposeRule() {
  @Suppress("unused")
  fun getActivityRule(): Any = error("getActivityRule() must not be touched")
}
