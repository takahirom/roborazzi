package com.github.takahirom.roborazzi

import android.content.res.Configuration
import androidx.test.core.app.ActivityScenario
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    assertNull(parameter.renderScaleBaseConfiguration)
    assertRestored(original, 1f)
  }

  @Test fun leavesCustomTestersInControlOfConfiguration() {
    val original = RuntimeEnvironment.getQualifiers()
    val custom = object : ComposePreviewTester<AndroidPreviewJUnit4TestParameter> {
      override fun testParameters() = emptyList<AndroidPreviewJUnit4TestParameter>()
      override fun test(testParameter: AndroidPreviewJUnit4TestParameter) = Unit
    }
    val rule = createRoborazziPreviewConfigurationRule(custom, parameter())
    rule.apply(statement { assertRestored(original, 1f) }, description).evaluate()
    assertRestored(original, 1f)
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
  private fun parameter(previewInfo: AndroidPreviewInfo = info): AndroidPreviewJUnit4TestParameter {
    val preview = Proxy.newProxyInstance(ComposablePreview::class.java.classLoader,
      arrayOf(ComposablePreview::class.java)) { _, method, _ ->
      when (method.name) {
        "getPreviewInfo" -> previewInfo
        // Read while resolving a per-preview renderScale override.
        "getDeclaringClass" -> PreviewConfigurationRuleTest::class.java.name
        "getMethodName" -> "previewWithoutOverride"
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
