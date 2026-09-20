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
class PreviewRenderScaleTest {
  private val composeRule = createAndroidComposeRule<RoborazziActivity>()
  private val temporaryFolder = TemporaryFolder()

  @get:Rule
  val rule: RuleChain = RuleChain.outerRule(temporaryFolder)
    .around(object : TestWatcher() {
      override fun starting(description: Description) {
        registerRoborazziActivityToRobolectricIfNeeded()
      }
    }).around(composeRule)

  @Test fun defaultScale() = capture(1.0)
  @Test fun halfScale() = capture(0.5)
  @Test fun doubleScale() = capture(2.0)
  @Test fun resizeIsIndependent() = capture(0.5, resizeScale = 0.5)
  @Test fun customPixelDevice() = capture(
    0.5, device = "spec:width=1080px,height=2340px,dpi=440", widthDp = 392, heightDp = 850, dpi = 440
  )
  @Test fun namedDevice() = capture(
    0.5, device = "id:pixel_5", widthDp = 392, heightDp = 850, dpi = 440
  )
  @Test fun namedDeviceDefaultScale() = capture(
    1.0, device = "id:pixel_5", widthDp = 392, heightDp = 850, dpi = 440
  )
  @Test fun previewSizeOverridesDevice() = capture(
    0.5, device = "id:pixel_5", widthDp = 200, heightDp = 300, dpi = 440, explicitSize = true
  )
  @Test fun oneThirdRoundsDensity() = capture(1.0 / 3)
  @Test fun densityIsClampedToOneDpi() = capture(0.001)

  private fun capture(
    scale: Double,
    resizeScale: Double = 1.0,
    device: String = "",
    widthDp: Int = 400,
    heightDp: Int = 800,
    dpi: Int = 320,
    explicitSize: Boolean = false,
  ) {
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
      override val declaringClass = PreviewRenderScaleTest::class.java.name
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
    try {
      ComposePreviewTester.defaultOptionsFromPlugin = ComposePreviewTester.Options(renderScale = scale)
      tester.test(AndroidPreviewJUnit4TestParameter({ composeRule }, preview))
      val expectedDpi = kotlin.math.round(dpi * scale).toInt().coerceAtLeast(1)
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
      ComposePreviewTester.defaultOptionsFromPlugin = oldOptions
      RuntimeEnvironment.setQualifiers(originalQualifiers)
    }
  }
}
