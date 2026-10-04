package com.github.takahirom.roborazzi

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ComposePreviewTester.TestParameter.JUnit4TestParameter.AndroidPreviewJUnit4TestParameter
import com.github.takahirom.roborazzi.annotations.ManualClockOptions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

/**
 * https://github.com/takahirom/roborazzi/issues/948
 *
 * A custom Capturer that rebuilds `parameter.roborazziComposeOptions` via
 * `builder().build()` must not change how ManualClockOptions advances the clock.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ManualClockOptionsRebuildTest {
  private val composeRule =
    ComposePreviewTester.Options.JUnit4TestLifecycleOptions().composeRuleFactory()
  private val temporaryFolder = TemporaryFolder()

  @get:Rule
  val rule: RuleChain = RuleChain.outerRule(temporaryFolder)
    .around(object : TestWatcher() {
      override fun starting(description: Description) {
        registerRoborazziActivityToRobolectricIfNeeded()
      }
    }).around(composeRule)

  @Test
  fun passThroughCapturerAdvancesClockOnce() = assertAdvancedToPeak { it }

  @Test
  fun emptyRebuildCapturerAdvancesClockOnce() = assertAdvancedToPeak { it.builder().build() }

  @Test
  fun fontScaleRebuildCapturerAdvancesClockOnce() =
    assertAdvancedToPeak { it.builder().fontScale(1.0f).build() }

  @Test
  fun builderDoesNotDuplicateOptionsImplementingMultipleInterfaces() {
    var setupCalls = 0
    var beforeCaptureCalls = 0
    val option = object : RoborazziComposeSetupOption, RoborazziComposeCaptureOption {
      override fun configure(configBuilder: RoborazziComposeSetupOption.ConfigBuilder) {
        setupCalls++
      }

      override fun beforeCapture() {
        beforeCaptureCalls++
      }

      override fun afterCapture() = Unit
    }
    val rebuilt = RoborazziComposeOptions { addOption(option) }.builder().build()

    @OptIn(InternalRoborazziApi::class)
    rebuilt.applySetup()
    rebuilt.beforeCapture()

    assertEquals("configure() calls", 1, setupCalls)
    assertEquals("beforeCapture() calls", 1, beforeCaptureCalls)
  }

  private fun assertAdvancedToPeak(rebuild: (RoborazziComposeOptions) -> RoborazziComposeOptions) {
    val file = temporaryFolder.newFile("preview.png")
    val startTime = composeRule.mainClock.currentTime
    var progressAtCapture = -1f
    val preview = object : ComposablePreview<AndroidPreviewInfo> {
      override val previewInfo = AndroidPreviewInfo()
      override val previewIndex: Int? = null
      override val previewIndexDisplayName: String? = null
      override val otherAnnotationsInfo = null
      override val declaringClass = ManualClockOptionsRebuildTest::class.java.name
      override val methodName = "pulsingBoxAtPeak"
      override val methodParametersType = ""

      @Composable
      override fun invoke() {
        val progress = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
          progress.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
              animation = tween(durationMillis = 500, easing = EaseInOut),
              repeatMode = RepeatMode.Reverse,
            ),
          )
        }
        progressAtCapture = progress.value
        Box(
          Modifier
            .size(96.dp)
            .background(lerp(Color.White, Color.Red, progress.value))
        )
      }
    }
    val tester = AndroidComposePreviewTester { parameter ->
      val composeOptions = rebuild(parameter.roborazziComposeOptions)
      AndroidComposePreviewTester.DefaultCapturer().capture(
        parameter.copy(
          filePath = file.absolutePath,
          roborazziComposeOptions = composeOptions,
          roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
        )
      )
    }

    tester.test(
      AndroidPreviewJUnit4TestParameter(
        composeTestRuleFactory = { composeRule },
        preview = preview,
        composeRoboComposePreviewOptionVariation = RoboComposePreviewOptionVariation(
          ManualClockOptions(advanceTimeMillis = 500L)
        ),
      )
    )

    val clockAdvanced = composeRule.mainClock.currentTime - startTime
    // advanceTimeBy rounds up to whole 16ms frames: 500ms -> 512ms.
    assertEquals("clock advanced by ManualClockOptions", 512L, clockAdvanced)
    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
    val center = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
    assertEquals(
      "center pixel at the animation peak (clock advanced ${clockAdvanced}ms, " +
        "progress=$progressAtCapture)",
      Integer.toHexString(Color.Red.toArgb()),
      Integer.toHexString(center),
    )
  }
}
