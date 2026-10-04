package io.github.takahirom.roborazzi

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.AwtRoboCanvas
import com.github.takahirom.roborazzi.DefaultFileNameGenerator
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.RoboVideoClock
import com.github.takahirom.roborazzi.RoboVideoOptions
import com.github.takahirom.roborazzi.RoboVideoRecorderScope
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.fileWithRecordFilePathStrategy
import com.github.takahirom.roborazzi.provideRoborazziContext
import com.github.takahirom.roborazzi.recordRoboVideoToFile
import java.awt.image.BufferedImage
import java.io.File

/**
 * Records [node] (the whole root by default) as a video (an animated image) with a fixed frame
 * rate, the Desktop counterpart of the Android `recordRoboVideo`. Only the bounds of [node] are
 * captured, so the output shows the content itself, like [captureRoboImage] does.
 *
 * The output format is chosen by the file extension: `.gif` (256 colors; the default) or `.png`
 * (a lossless, full-color APNG).
 *
 * The Compose main clock is paused while recording. [setup] runs with the clock paused and before
 * the first frame, so install the content there (`setContent { ... }`) to record its entrance
 * animations. [block] runs next; call `delay()` in it to advance virtual time. Recording then
 * continues until the UI settles (see [RoboVideoOptions.settleTimeoutMillis]).
 *
 * This is a no-op unless the Roborazzi task is recording.
 */
@ExperimentalRoborazziApi
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.recordRoboVideo(
  filePath: String = DefaultFileNameGenerator.generateFilePath("gif"),
  videoOptions: RoboVideoOptions = RoboVideoOptions(),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  node: ComposeUiTest.() -> SemanticsNodeInteraction = { onRoot() },
  setup: ComposeUiTest.() -> Unit = {},
  block: RoboVideoRecorderScope.() -> Unit = {},
) {
  recordRoboVideo(
    file = fileWithRecordFilePathStrategy(filePath),
    videoOptions = videoOptions,
    roborazziOptions = roborazziOptions,
    node = node,
    setup = setup,
    block = block,
  )
}

@ExperimentalRoborazziApi
@OptIn(ExperimentalTestApi::class, InternalRoborazziApi::class)
fun ComposeUiTest.recordRoboVideo(
  file: File,
  videoOptions: RoboVideoOptions = RoboVideoOptions(),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  node: ComposeUiTest.() -> SemanticsNodeInteraction = { onRoot() },
  setup: ComposeUiTest.() -> Unit = {},
  block: RoboVideoRecorderScope.() -> Unit = {},
) {
  // currently, video compare is not supported
  if (!roborazziOptions.taskType.isRecording()) return
  val test = this
  recordRoboVideoToFile(
    file = file,
    videoOptions = videoOptions,
    roborazziOptions = roborazziOptions,
    clock = ComposeUiTestRoboVideoClock(test),
    setup = { test.setup() },
    captureFrame = { onCanvas ->
      // Re-resolve the node every frame so the capture follows its current bounds.
      val awtImage = test.node().captureToImage().toAwtImage()
      val canvas = AwtRoboCanvas(
        width = awtImage.width,
        height = awtImage.height,
        filled = true,
        bufferedImageType = BufferedImage.TYPE_INT_ARGB
      )
      canvas.drawImage(awtImage)
      onCanvas(canvas)
    },
    block = block,
  )
}

/** Adapts a [ComposeUiTest] to the shared recording loop. */
@OptIn(ExperimentalTestApi::class, InternalRoborazziApi::class)
private class ComposeUiTestRoboVideoClock(private val test: ComposeUiTest) : RoboVideoClock {
  override fun <T> withManualClock(body: () -> T): T {
    val mainClock = test.mainClock
    val wasAutoAdvance = mainClock.autoAdvance
    test.waitForIdle()
    mainClock.autoAdvance = false
    try {
      return body()
    } finally {
      mainClock.autoAdvance = wasAutoAdvance
      test.waitForIdle()
    }
  }

  override fun advanceBy(stepMillis: Long) {
    test.mainClock.advanceTimeBy(stepMillis, ignoreFrameDuration = true)
    test.waitForIdle()
  }
}
