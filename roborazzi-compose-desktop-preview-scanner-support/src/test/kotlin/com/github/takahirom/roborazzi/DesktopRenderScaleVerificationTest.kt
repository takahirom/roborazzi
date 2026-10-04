package com.github.takahirom.roborazzi

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
class DesktopRenderScaleVerificationTest {
  @After
  fun tearDown() {
    // The options have no default profile to fall back on, so the reset names one explicitly.
    DesktopComposePreviewTester.defaultOptionsFromPlugin =
      DesktopComposePreviewTester.Options(deviceProfile = DesktopPreviewDeviceProfile.Desktop)
    provideRoborazziContext().clearRuleOverrideRoborazziOptions()
  }

  @Test
  fun `a tester that keeps the plugin options passes`() {
    configurePlugin(renderScale = 0.5, taskType = RoborazziTaskType.Record)

    DesktopRenderScaleVerification.verify(OptionsFromPluginTester())
  }

  @Test
  fun `a tester that builds its options from scratch is reported`() {
    configurePlugin(renderScale = 0.5, taskType = RoborazziTaskType.Record)

    val message = assertFails(RenderScaleDroppingTester())
    assertTrue(message, message.contains("renderScale = 0.5"))
    assertTrue(message, message.contains(RenderScaleDroppingTester::class.java.name))
    assertTrue(message, message.contains("options().renderScale returned 1.0"))
  }

  @Test
  fun `a tester may pick its own scale when none is configured`() {
    configurePlugin(renderScale = 1.0, taskType = RoborazziTaskType.Record)

    DesktopRenderScaleVerification.verify(OwnScaleTester())
  }

  @Test
  fun `nothing is reported when Roborazzi does not capture`() {
    configurePlugin(renderScale = 0.5, taskType = RoborazziTaskType.None)

    DesktopRenderScaleVerification.verify(RenderScaleDroppingTester())
  }

  private fun assertFails(tester: DesktopComposePreviewTester): String {
    try {
      DesktopRenderScaleVerification.verify(tester)
    } catch (e: IllegalStateException) {
      return requireNotNull(e.message)
    }
    fail("Expected the dropped renderScale to be reported")
    error("unreachable")
  }

  private fun configurePlugin(renderScale: Double, taskType: RoborazziTaskType) {
    DesktopComposePreviewTester.defaultOptionsFromPlugin =
      DesktopComposePreviewTester.Options(
        deviceProfile = DesktopPreviewDeviceProfile.Desktop,
        renderScale = renderScale,
      )
    provideRoborazziContext().setRuleOverrideRoborazziOptions(RoborazziOptions(taskType = taskType))
  }

  private class OptionsFromPluginTester : DesktopComposePreviewTester {
    override fun testParameters(): List<DesktopPreviewTestParameter> = emptyList()
    override fun test(testParameter: DesktopPreviewTestParameter) = Unit
  }

  /** Stands in for a custom tester that never carries the configured scale into its capture. */
  private class RenderScaleDroppingTester : DesktopComposePreviewTester {
    override fun options() =
      DesktopComposePreviewTester.Options(deviceProfile = DesktopPreviewDeviceProfile.Desktop)
    override fun testParameters(): List<DesktopPreviewTestParameter> = emptyList()
    override fun test(testParameter: DesktopPreviewTestParameter) = Unit
  }

  private class OwnScaleTester : DesktopComposePreviewTester {
    override fun options() = DesktopComposePreviewTester.Options(
      deviceProfile = DesktopPreviewDeviceProfile.Desktop,
      renderScale = 0.5,
    )
    override fun testParameters(): List<DesktopPreviewTestParameter> = emptyList()
    override fun test(testParameter: DesktopPreviewTestParameter) = Unit
  }
}
