package com.github.takahirom.roborazzi

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.test.core.app.ActivityScenario
import java.lang.reflect.Proxy
import com.github.takahirom.roborazzi.annotations.RoboComposePreviewOptions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview
import com.github.takahirom.roborazzi.ComposePreviewTester.TestParameter.JUnit4TestParameter.AndroidPreviewJUnit4TestParameter
import com.github.takahirom.roborazzi.ComposePreviewTester.TestParameter.JUnit4TestParameter

@OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w200dp-h400dp-notnight-160dpi")
class PreviewConfigurationRuleTest {
  private val description = Description.createTestDescription(javaClass, "preview")
  private val info = AndroidPreviewInfo(
    device = "spec:width=100dp,height=120dp,dpi=320",
    widthDp = 100, heightDp = 120, fontScale = 2f,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
  )

  @Test fun appliesBeforeActivityLaunchAndRestoresAfterTeardown() {
    RuntimeEnvironment.setFontScale(1.25f)
    val original = RuntimeEnvironment.getQualifiers()
    var reachedTeardown = false
    val inner = TestRule { base, _ -> statement {
      assertPreviewConfiguration()
      registerRoborazziActivityToRobolectricIfNeeded()
      ActivityScenario.launch(RoborazziActivity::class.java).use { scenario ->
        scenario.onActivity { activity ->
          assertEquals(100, activity.resources.configuration.screenWidthDp)
          assertEquals(2f, activity.resources.configuration.fontScale, 0f)
        }
        try { base.evaluate() } finally {
          assertPreviewConfiguration()
          reachedTeardown = true
        }
      }
    } }
    val rule = createRoborazziPreviewConfigurationRule(AndroidComposePreviewTester(), parameter())
    rule.apply(inner.apply(statement { assertPreviewConfiguration() }, description), description).evaluate()
    assertEquals(true, reachedTeardown)
    assertRestored(original, 1.25f)
  }

  @Test fun restoresAfterInnerRuleFailure() {
    val original = RuntimeEnvironment.getQualifiers()
    val expected = IllegalStateException("inner rule failed")
    val rule = createRoborazziPreviewConfigurationRule(AndroidComposePreviewTester(), parameter())
    try {
      rule.apply(statement {
        assertPreviewConfiguration()
        throw expected
      }, description).evaluate()
      fail("Expected inner rule failure")
    } catch (actual: IllegalStateException) { assertSame(expected, actual) }
    assertRestored(original, 1f)
  }

  @Test fun restoresAfterPartiallyAppliedConfigurationFails() {
    val original = RuntimeEnvironment.getQualifiers()
    val parameter = parameter(info.copy(locale = "invalid_qualifier"))
    val rule = createRoborazziPreviewConfigurationRule(AndroidComposePreviewTester(), parameter)
    try {
      rule.apply(statement { fail("The body must not run") }, description).evaluate()
      fail("Expected invalid qualifier failure")
    } catch (_: IllegalArgumentException) { }
    assertRestored(original, 1f)
  }

  @Test fun leavesCustomTestersInControlOfConfigurationWithoutAScale() {
    val original = RuntimeEnvironment.getQualifiers()
    val rule = createRoborazziPreviewConfigurationRule(CustomTester(), parameter())
    rule.apply(statement { assertRestored(original, 1f) }, description).evaluate()
    assertRestored(original, 1f)
  }

  @Test fun scalesOnlyTheConfigDensityForCustomTesters() {
    configurePlugin(renderScale = 0.5)
    val original = RuntimeEnvironment.getQualifiers()

    // A custom tester may capture without the preview's options, so the preview's device, font
    // scale and night mode must not leak into its capture; only the scale should.
    createRoborazziPreviewConfigurationRule(CustomTester(), parameter())
      .apply(statement {
        val config = RuntimeEnvironment.getApplication().resources.configuration
        assertEquals(80, config.densityDpi)
        assertEquals(200, config.screenWidthDp)
        assertEquals(400, config.screenHeightDp)
        assertEquals(Configuration.UI_MODE_NIGHT_NO, config.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        assertEquals(1f, RuntimeEnvironment.getFontScale(), 0f)
      }, description).evaluate()
    assertRestored(original, 1f)
  }

  @Suppress("UNCHECKED_CAST")
  @Test fun scalesTheConfigDensityForTestersOfNonAndroidPreviews() {
    configurePlugin(renderScale = 0.5)
    // Like a tester built on the scanner's common module, whose previews are not Android ones.
    val tester = object : ComposePreviewTester<JUnit4TestParameter<Unit>> {
      override fun testParameters() = emptyList<JUnit4TestParameter<Unit>>()
      override fun test(testParameter: JUnit4TestParameter<Unit>) = Unit
    }
    val parameter = JUnit4TestParameter(
      composeTestRuleFactory = { error("Configuration must not create a Compose rule") },
      preview = parameter().preview as ComposablePreview<*> as ComposablePreview<Unit>,
    )

    createRoborazziPreviewConfigurationRule(tester, parameter)
      .apply(statement {
        assertEquals(80, RuntimeEnvironment.getApplication().resources.configuration.densityDpi)
      }, description).evaluate()
  }

  @Test fun appliesThePreviewOverrideForCustomTesters() {
    val parameter = parameter(methodName = "previewWithOverride")

    // Nothing is configured in Gradle, but the preview asks for 0.25 of the @Config's 160dpi.
    createRoborazziPreviewConfigurationRule(CustomTester(), parameter)
      .apply(statement {
        assertEquals(40, RuntimeEnvironment.getApplication().resources.configuration.densityDpi)
      }, description).evaluate()
  }

  @Test fun consecutiveExecutionsStartWithOriginalConfiguration() {
    val original = RuntimeEnvironment.getQualifiers()
    repeat(2) {
      assertRestored(original, 1f)
      createRoborazziPreviewConfigurationRule(AndroidComposePreviewTester(), parameter())
        .apply(statement { assertPreviewConfiguration() }, description).evaluate()
      assertRestored(original, 1f)
    }
  }

  @Test fun appliesThePreviewOverrideRatherThanTheConfiguredScale() {
    configurePlugin(renderScale = 0.5)
    val parameter = parameter(methodName = "previewWithOverride")

    createRoborazziPreviewConfigurationRule(AndroidComposePreviewTester(), parameter)
      .apply(statement {
        // 320dpi from the preview's device, scaled by its own 0.25 rather than the plugin's 0.5.
        assertEquals(80, RuntimeEnvironment.getApplication().resources.configuration.densityDpi)
      }, description).evaluate()
  }

  @Test fun scalesABlankDeviceFromTheConfigEvenAfterItsQualifiersChanged() {
    val preview = parameter(info.copy(device = "", widthDp = -1, heightDp = -1)).preview
    RuntimeEnvironment.setQualifiers("w300dp-h500dp-480dpi")

    // Applying the scale twice, as the rule and the capture do, must not scale it twice.
    repeat(2) {
      preview.toRoborazziComposeOptions(renderScale = 0.5).applySetup()
      val config = RuntimeEnvironment.getApplication().resources.configuration
      // The @Config describes 200x400dp at 160dpi; the qualifiers set above do not count.
      assertEquals(80, config.densityDpi)
      assertEquals(200, config.screenWidthDp)
      assertEquals(400, config.screenHeightDp)
    }
  }

  @RoboComposePreviewOptions(renderScale = 0.25)
  @Composable fun previewWithOverride() = Unit

  /** Reports the configured scale through the default options(), like a delegating tester. */
  private class CustomTester : ComposePreviewTester<AndroidPreviewJUnit4TestParameter> {
    override fun testParameters() = emptyList<AndroidPreviewJUnit4TestParameter>()
    override fun test(testParameter: AndroidPreviewJUnit4TestParameter) = Unit
  }

  private fun configurePlugin(renderScale: Double) {
    ComposePreviewTester.defaultOptionsFromPlugin =
      ComposePreviewTester.Options(renderScale = renderScale)
    provideRoborazziContext().setRuleOverrideRoborazziOptions(
      RoborazziOptions(taskType = RoborazziTaskType.Record)
    )
  }

  @After fun resetPluginOptions() {
    ComposePreviewTester.defaultOptionsFromPlugin = ComposePreviewTester.Options()
    provideRoborazziContext().clearRuleOverrideRoborazziOptions()
  }

  private fun assertPreviewConfiguration() {
    val config = RuntimeEnvironment.getApplication().resources.configuration
    assertEquals(100, config.screenWidthDp)
    assertEquals(120, config.screenHeightDp)
    assertEquals(320, config.densityDpi)
    assertEquals(Configuration.UI_MODE_NIGHT_YES, config.uiMode and Configuration.UI_MODE_NIGHT_MASK)
    assertEquals(2f, RuntimeEnvironment.getFontScale(), 0f)
  }

  private fun assertRestored(qualifiers: String, font: Float) {
    assertEquals(qualifiers, RuntimeEnvironment.getQualifiers())
    assertEquals(font, RuntimeEnvironment.getFontScale(), 0f)
    assertEquals(font, RuntimeEnvironment.getApplication().resources.configuration.fontScale, 0f)
  }

  @Suppress("UNCHECKED_CAST")
  private fun parameter(
    previewInfo: AndroidPreviewInfo = info,
    methodName: String = "previewWithoutOverride",
  ): AndroidPreviewJUnit4TestParameter {
    val preview = Proxy.newProxyInstance(ComposablePreview::class.java.classLoader,
      arrayOf(ComposablePreview::class.java)) { _, method, _ ->
      when (method.name) {
        "getPreviewInfo" -> previewInfo
        // Read while resolving a per-preview renderScale override.
        "getDeclaringClass" -> PreviewConfigurationRuleTest::class.java.name
        "getMethodName" -> methodName
        "getMethodParametersType" -> ""
        else -> error("Unexpected preview access: $method")
      }
    } as ComposablePreview<AndroidPreviewInfo>
    return AndroidPreviewJUnit4TestParameter(
      composeTestRuleFactory = { error("Configuration must not create a Compose rule") }, preview = preview
    )
  }

  private fun statement(block: () -> Unit) = object : Statement() {
    override fun evaluate() = block()
  }
}
