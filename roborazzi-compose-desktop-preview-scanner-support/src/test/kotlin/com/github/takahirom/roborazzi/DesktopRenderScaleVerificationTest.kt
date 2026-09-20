package com.github.takahirom.roborazzi

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo

@OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
class DesktopRenderScaleVerificationTest {
  private val tester = RenderScaleDroppingTester()
  private val previewInfo = AndroidPreviewInfo()

  @After
  fun tearDown() {
    DesktopComposePreviewTester.defaultOptionsFromPlugin = DesktopComposePreviewTester.Options()
    provideRoborazziContext().clearRuleOverrideRoborazziOptions()
  }

  @Test
  fun `a tester that sizes its own surface through resolve passes`() {
    // The message the check prints tells a custom tester to call
    // DesktopPreviewRenderSpec.resolve(previewInfo, renderProfile, renderScale). A tester that
    // follows it has applied the scale, so it must not be failed for it.
    configurePlugin(renderScale = 0.5, taskType = RoborazziTaskType.Record)

    DesktopRenderScaleVerification.beforeTest()
    DesktopPreviewRenderSpec.resolve(previewInfo, DesktopPreviewRenderProfile.Desktop, 0.5)

    DesktopRenderScaleVerification.afterTest(tester)
  }

  @Test
  fun `a tester that never resolves the spec is reported`() {
    configurePlugin(renderScale = 0.5, taskType = RoborazziTaskType.Record)

    DesktopRenderScaleVerification.beforeTest()

    val message = assertFails()
    assertTrue(message, message.contains("renderScale = 0.5"))
    assertTrue(message, message.contains(RenderScaleDroppingTester::class.java.name))
  }

  @Test
  fun `a tester that resolves a different scale is reported`() {
    configurePlugin(renderScale = 0.5, taskType = RoborazziTaskType.Record)

    DesktopRenderScaleVerification.beforeTest()
    DesktopPreviewRenderSpec.resolve(previewInfo, DesktopPreviewRenderProfile.Desktop, 0.75)

    val message = assertFails()
    assertTrue(message, message.contains("applied 0.75 instead"))
  }

  @Test
  fun `grouping previews into scenes does not count as applying the scale`() {
    // Scene grouping resolves every preview before any capture runs. If that counted, scene reuse
    // would silently switch the check off for the testers it exists to catch.
    configurePlugin(renderScale = 0.5, taskType = RoborazziTaskType.Record)

    DesktopRenderScaleVerification.beforeTest()
    groupDesktopPreviewsByScene(
      parameters = listOf(fakeParameter("a"), fakeParameter("b")),
      profile = DesktopPreviewRenderProfile.Desktop,
      renderScale = 0.5,
    )

    assertTrue(assertFails().contains("renderScale = 0.5"))
  }

  private fun assertFails(): String {
    try {
      DesktopRenderScaleVerification.afterTest(tester)
    } catch (e: IllegalStateException) {
      return requireNotNull(e.message)
    }
    fail("Expected the dropped renderScale to be reported")
    error("unreachable")
  }

  private fun configurePlugin(renderScale: Double, taskType: RoborazziTaskType) {
    DesktopComposePreviewTester.defaultOptionsFromPlugin =
      DesktopComposePreviewTester.Options(renderScale = renderScale)
    provideRoborazziContext().setRuleOverrideRoborazziOptions(RoborazziOptions(taskType = taskType))
  }

  /** Stands in for a custom tester that never carries the configured scale into its capture. */
  private class RenderScaleDroppingTester : DesktopComposePreviewTester {
    override fun testParameters(): List<DesktopPreviewTestParameter> = emptyList()
    override fun test(testParameter: DesktopPreviewTestParameter) = Unit
  }
}
