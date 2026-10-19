package com.github.takahirom.roborazzi

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RenderScaleVerificationTest {
  @After fun tearDown() {
    ComposePreviewTester.defaultOptionsFromPlugin = ComposePreviewTester.Options()
    provideRoborazziContext().clearRuleOverrideRoborazziOptions()
  }

  @Test fun passesWhenNoScaleIsConfigured() {
    configurePlugin(renderScale = 1.0, taskType = RoborazziTaskType.Record)

    RenderScaleVerification.verify(Tester(renderScale = 1.0))
  }

  @Test fun passesWhenTheTesterReportsTheConfiguredScale() {
    configurePlugin(renderScale = 0.5, taskType = RoborazziTaskType.Record)

    RenderScaleVerification.verify(Tester(renderScale = 0.5))
  }

  @Test fun failsWhenTheTesterDropsTheScale() {
    configurePlugin(renderScale = 0.5, taskType = RoborazziTaskType.Record)

    val message = assertFails(Tester(renderScale = 1.0))
    assertTrue(message, message.contains("renderScale = 0.5"))
    assertTrue(message, message.contains(Tester::class.java.name))
    assertTrue(message, message.contains("returned 1.0"))
    assertTrue(message, message.contains("super.options().copy("))
    assertTrue(message, message.contains("toRoborazziComposeOptions(renderScale)"))
  }

  @Test fun failsWhenTheTesterReportsADifferentScale() {
    configurePlugin(renderScale = 0.5, taskType = RoborazziTaskType.Record)

    val message = assertFails(Tester(renderScale = 0.75))
    assertTrue(message, message.contains("returned 0.75"))
  }

  @Test fun passesWhenATesterScalesOnItsOwnWithoutAnyConfiguration() {
    configurePlugin(renderScale = 1.0, taskType = RoborazziTaskType.Record)

    // Nobody asked for a scale, so a tester is free to scale the capture as it sees fit.
    RenderScaleVerification.verify(Tester(renderScale = 0.5))
  }

  @Test fun passesWhenRoborazziIsNotRecordingOrVerifying() {
    configurePlugin(renderScale = 0.5, taskType = RoborazziTaskType.None)

    RenderScaleVerification.verify(Tester(renderScale = 1.0))
  }

  @Test fun afterTestKeepsVerifyingForTestsGeneratedByAnOlderPlugin() {
    configurePlugin(renderScale = 0.5, taskType = RoborazziTaskType.Record)

    RenderScaleVerification.beforeTest()
    try {
      RenderScaleVerification.afterTest(Tester(renderScale = 1.0))
      fail("Expected the dropped renderScale to be reported")
    } catch (e: IllegalStateException) {
      assertTrue(e.message, requireNotNull(e.message).contains("renderScale = 0.5"))
    }
  }

  private fun assertFails(tester: Tester): String {
    try {
      RenderScaleVerification.verify(tester)
    } catch (e: IllegalStateException) {
      return requireNotNull(e.message)
    }
    fail("Expected the renderScale mismatch to be reported")
    error("unreachable")
  }

  private fun configurePlugin(renderScale: Double, taskType: RoborazziTaskType) {
    ComposePreviewTester.defaultOptionsFromPlugin =
      ComposePreviewTester.Options(renderScale = renderScale)
    provideRoborazziContext().setRuleOverrideRoborazziOptions(
      RoborazziOptions(taskType = taskType)
    )
  }

  /** Stands in for a custom tester whose options() reports [renderScale]. */
  private class Tester(
    private val renderScale: Double,
  ) : ComposePreviewTester<ComposePreviewTester.TestParameter<*>> {
    override fun options() = super.options().copy(renderScale = renderScale)
    override fun testParameters(): List<ComposePreviewTester.TestParameter<*>> = emptyList()
    override fun test(testParameter: ComposePreviewTester.TestParameter<*>) = Unit
  }
}
