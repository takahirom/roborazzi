package com.github.takahirom.roborazzi

import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.IntSize
import com.github.takahirom.roborazzi.ComposePreviewTester.TestParameter.JUnit4TestParameter.AndroidPreviewJUnit4TestParameter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

@OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h800dp-320dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreviewRenderScaleConfigurationRuleTest {
  @get:Rule val temporaryFolder = TemporaryFolder()

  @Test fun copyingOptionsPreservesRenderScale() {
    val options = ComposePreviewTester.Options().apply {
      renderScale = 0.5f
    }

    assertEquals(0.5f, options.copy().renderScale)
  }

  @Test fun blankDeviceIsScaledOnlyOnce() = capture(0.5f)
  @Test fun blankDeviceWithRoundedDensityIsScaledOnlyOnce() = capture(1f / 3f)
  @Test fun namedDeviceIsConfiguredBeforeLaunch() = capture(
    1f / 3f, device = "id:pixel_5", widthDp = 392, heightDp = 850, dpi = 440
  )
  @Test fun pixelDeviceIsConfiguredBeforeLaunch() = capture(
    0.5f, device = "spec:width=1080px,height=2340px,dpi=440", widthDp = 392, heightDp = 850, dpi = 440
  )

  private fun capture(
    scale: Float,
    resizeScale: Double = 1.0,
    device: String = "",
    widthDp: Int = 400,
    heightDp: Int = 800,
    dpi: Int = 320,
    explicitSize: Boolean = false,
  ) {
    val composeRule = createAndroidComposeRule<RoborazziActivity>()
    val oldOptions = ComposePreviewTester.defaultOptionsFromPlugin
    val originalQualifiers = RuntimeEnvironment.getQualifiers()
    val file = temporaryFolder.newFile("preview.png")
    var measuredSize = IntSize.Zero
    var density = 0f
    var config: Configuration? = null
    var surfaceSize = IntSize.Zero
    val preview = object : ComposablePreview<AndroidPreviewInfo> {
      override val previewInfo = AndroidPreviewInfo(
        device = device, fontScale = 1.3f, uiMode = Configuration.UI_MODE_NIGHT_YES,
        widthDp = if (explicitSize) widthDp else -1,
        heightDp = if (explicitSize) heightDp else -1,
      )
      override val previewIndex: Int? = null
      override val previewIndexDisplayName: String? = null
      override val otherAnnotationsInfo = null
      override val declaringClass = PreviewRenderScaleConfigurationRuleTest::class.java.name
      override val methodName = "preview"
      override val methodParametersType = ""

      @Composable override fun invoke() {
        density = LocalDensity.current.density
        assertEquals(1.3f, LocalDensity.current.fontScale)
        config = Configuration(LocalConfiguration.current)
        Box(Modifier.fillMaxSize().onSizeChanged { measuredSize = it })
      }
    }
    val tester = AndroidComposePreviewTester { parameter ->
      val options = parameter.roborazziComposeOptions.builder().addOption(
        object : RoborazziComposeCaptureOption {
          override fun beforeCapture() {
            // ShadowPixelCopy renders the decorView into an ImageReader of this size.
            val decor = composeRule.activity.window.decorView
            surfaceSize = IntSize(decor.width, decor.height)
          }
          override fun afterCapture() = Unit
        }
      ).build()
      AndroidComposePreviewTester.DefaultCapturer().capture(parameter.copy(
        filePath = file.absolutePath,
        roborazziComposeOptions = options,
        roborazziOptions = RoborazziOptions(
          taskType = RoborazziTaskType.Record,
          recordOptions = RoborazziOptions.RecordOptions(resizeScale = resizeScale)
        )
      ))
    }
    var created = 0
    var destroyed = 0
    var startupConfiguration: Configuration? = null
    val application = RuntimeEnvironment.getApplication()
    val observer = object : android.app.Application.ActivityLifecycleCallbacks {
      override fun onActivityCreated(activity: android.app.Activity, state: android.os.Bundle?) {
        created++
        startupConfiguration = Configuration(activity.resources.configuration)
      }
      override fun onActivityDestroyed(activity: android.app.Activity) { destroyed++ }
      override fun onActivityStarted(activity: android.app.Activity) = Unit
      override fun onActivityResumed(activity: android.app.Activity) = Unit
      override fun onActivityPaused(activity: android.app.Activity) = Unit
      override fun onActivityStopped(activity: android.app.Activity) = Unit
      override fun onActivitySaveInstanceState(activity: android.app.Activity, state: android.os.Bundle) = Unit
    }
    application.registerActivityLifecycleCallbacks(observer)
    try {
      ComposePreviewTester.defaultOptionsFromPlugin = ComposePreviewTester.Options().apply {
        renderScale = scale
      }
      val parameter = AndroidPreviewJUnit4TestParameter({ composeRule }, preview)
      val rules = parameter.releaseComposeTestRuleAfter {
        RuleChain.outerRule(createRoborazziPreviewConfigurationRule(tester, parameter))
          .around(object : TestWatcher() {
            override fun starting(description: Description) {
              registerRoborazziActivityToRobolectricIfNeeded()
            }
          }).around(composeRule)
      }
      rules.apply(object : org.junit.runners.model.Statement() {
        override fun evaluate() { tester.test(parameter) }
      }, Description.createTestDescription(javaClass, "scaledPreview")).evaluate()
      assertEquals("No recreation should be needed", 1, created)
      assertEquals(1, destroyed)
      org.junit.Assert.assertNull(parameter.renderScaleBaseConfiguration)
      val expectedDpi = kotlin.math.round(dpi * scale).toInt().coerceAtLeast(1)
      assertEquals(expectedDpi, requireNotNull(startupConfiguration).densityDpi)
      assertEquals(widthDp, requireNotNull(startupConfiguration).screenWidthDp)
      assertEquals(heightDp, requireNotNull(startupConfiguration).screenHeightDp)
      assertEquals(expectedDpi / 160f, density)
      val capturedConfiguration = requireNotNull(config)
      assertEquals(widthDp, capturedConfiguration.screenWidthDp)
      assertEquals(heightDp, capturedConfiguration.screenHeightDp)
      assertEquals(
        Configuration.UI_MODE_NIGHT_YES,
        capturedConfiguration.uiMode and Configuration.UI_MODE_NIGHT_MASK
      )
      assertEquals(widthDp.toFloat(), measuredSize.width / density, 1f / density)
      assertEquals(heightDp.toFloat(), measuredSize.height / density, 1f / density)
      assertTrue("Rendering surface must match scaled dimensions before PixelCopy: $surfaceSize", surfaceSize.width <= widthDp * density + 1)
      assertTrue("Rendering surface must match scaled dimensions before PixelCopy: $surfaceSize", surfaceSize.height <= heightDp * density + 1)
      assertEquals("Rendering surface width must match scaled pixel width", measuredSize.width, surfaceSize.width)
      assertEquals("Rendering surface height must match scaled pixel height", measuredSize.height, surfaceSize.height)
      val image = BitmapFactory.decodeFile(file.absolutePath)
      assertEquals((measuredSize.width * resizeScale).toInt(), image.width)
      assertEquals((measuredSize.height * resizeScale).toInt(), image.height)
      assertEquals(originalQualifiers, RuntimeEnvironment.getQualifiers())
    } finally {
      application.unregisterActivityLifecycleCallbacks(observer)
      ComposePreviewTester.defaultOptionsFromPlugin = oldOptions
      RuntimeEnvironment.setQualifiers(originalQualifiers)
    }
  }
}
