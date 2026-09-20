package com.github.takahirom.roborazzi

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

@OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h800dp-320dpi")
class RenderScaleVerificationTest {
  private val tester = DroppingTester()

  @After fun tearDown() {
    ComposePreviewTester.defaultOptionsFromPlugin = ComposePreviewTester.Options()
    provideRoborazziContext().clearRuleOverrideRoborazziOptions()
  }

  @Test fun passesWhenNoScaleIsConfigured() {
    configurePlugin(renderScale = 1.0, taskType = RoborazziTaskType.Record)

    RenderScaleVerification.beforeTest()

    RenderScaleVerification.afterTest(tester)
  }

  @Test fun passesWhenTheScaleReachesTheCapture() {
    configurePlugin(renderScale = 0.5, taskType = RoborazziTaskType.Record)

    RenderScaleVerification.beforeTest()
    preview.toRoborazziComposeOptions(renderScale = 0.5).applySetup()

    RenderScaleVerification.afterTest(tester)
  }

  @Test fun failsWhenTheTesterDropsTheScale() {
    configurePlugin(renderScale = 0.5, taskType = RoborazziTaskType.Record)

    RenderScaleVerification.beforeTest()

    try {
      RenderScaleVerification.afterTest(tester)
      fail("Expected the dropped renderScale to be reported")
    } catch (e: IllegalStateException) {
      val message = requireNotNull(e.message)
      assertTrue(message, message.contains("renderScale = 0.5"))
      assertTrue(message, message.contains(DroppingTester::class.java.name))
      assertTrue(message, message.contains("super.options().copy("))
      assertTrue(message, message.contains("toRoborazziComposeOptions(renderScale)"))
    }
  }

  @Test fun failsWhenTheTesterAppliesADifferentScale() {
    configurePlugin(renderScale = 0.5, taskType = RoborazziTaskType.Record)

    RenderScaleVerification.beforeTest()
    preview.toRoborazziComposeOptions(renderScale = 0.75).applySetup()

    val message = assertFails()
    assertTrue(message, message.contains("renderScale = 0.5"))
    assertTrue(message, message.contains("applied 0.75 instead"))
  }

  @Test fun failsWhenOnlySomeCapturesUseTheConfiguredScale() {
    configurePlugin(renderScale = 0.5, taskType = RoborazziTaskType.Record)

    RenderScaleVerification.beforeTest()
    preview.toRoborazziComposeOptions(renderScale = 0.5).applySetup()
    preview.toRoborazziComposeOptions(renderScale = 0.75).applySetup()

    val message = assertFails()
    assertTrue(message, message.contains("applied 0.5, 0.75 instead"))
  }

  @Test fun passesWhenRoborazziIsNotRecordingOrVerifying() {
    configurePlugin(renderScale = 0.5, taskType = RoborazziTaskType.None)

    RenderScaleVerification.beforeTest()

    RenderScaleVerification.afterTest(tester)
  }

  private fun assertFails(): String {
    try {
      RenderScaleVerification.afterTest(tester)
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

  /** Stands in for a custom tester that never carries [renderScale] through to the capture. */
  private class DroppingTester : ComposePreviewTester<ComposePreviewTester.TestParameter<*>> {
    override fun testParameters(): List<ComposePreviewTester.TestParameter<*>> = emptyList()
    override fun test(testParameter: ComposePreviewTester.TestParameter<*>) = Unit
  }

  private val preview = object : ComposablePreview<AndroidPreviewInfo> {
    override val previewInfo = AndroidPreviewInfo(
      device = "", fontScale = 1f, uiMode = Configuration.UI_MODE_NIGHT_NO,
      widthDp = -1, heightDp = -1,
    )
    override val previewIndex: Int? = null
    override val previewIndexDisplayName: String? = null
    override val otherAnnotationsInfo = null
    override val declaringClass = RenderScaleVerificationTest::class.java.name
    override val methodName = "preview"
    override val methodParametersType = ""

    @Composable override fun invoke() = Unit
  }
}
