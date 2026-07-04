package com.github.takahirom.roborazzi

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import com.dropbox.differ.ImageComparator
import java.io.File

/**
 * Options for [captureRoboAnimation].
 *
 * @param fps Frames per second of the recorded animation. The recording clock is advanced by
 * 1000 / fps milliseconds of virtual time per frame, so the output plays back in real time.
 * @param settleTimeoutMillis After [block] finishes, recording continues until the UI stops
 * changing, up to this amount of additional virtual time. This allows capturing animations
 * that are still running when the block ends.
 */
@ExperimentalRoborazziApi
data class RoboAnimationOptions(
  val fps: Int = 10,
  val settleTimeoutMillis: Long = 3_000,
) {
  init {
    require(fps in 1..100) { "fps must be in 1..100 but was $fps" }
  }

  val frameStepMillis: Long get() = 1_000L / fps
}

/**
 * Receiver scope of the [captureRoboAnimation] block. Interactions performed in the block run
 * with the Compose main clock paused; call [delay] to advance virtual time while recording
 * frames, similar to how a user would watch the animation play out.
 */
@ExperimentalRoborazziApi
class RoboAnimationRecorderScope internal constructor(
  private val composeRule: ComposeTestRule,
  private val frameStepMillis: Long,
  private val captureFrame: () -> Unit,
) {
  /**
   * Advances virtual time by [durationMillis], capturing a frame every 1000 / fps milliseconds.
   * This does not sleep; it steps the Compose main clock like
   * [androidx.compose.ui.test.MainTestClock.advanceTimeBy].
   */
  fun delay(durationMillis: Long) {
    var remainingMillis = durationMillis
    while (remainingMillis > 0) {
      val stepMillis = minOf(frameStepMillis, remainingMillis)
      composeRule.mainClock.advanceTimeBy(stepMillis)
      composeRule.waitForIdle()
      captureFrame()
      remainingMillis -= stepMillis
    }
  }
}

/**
 * Records the animation of this node as an animated image (currently GIF) with a fixed frame
 * rate, so that the output plays back the UI in real time. This is useful for creating videos
 * of animations that designers can review.
 *
 * Unlike [captureRoboGif], which only records visually distinct states with a fixed 1-second
 * delay, this API pauses the Compose main clock and drives it frame by frame while recording,
 * so intermediate animation frames are captured with faithful timing.
 *
 * ```kotlin
 * composeTestRule.onNodeWithTag("box").captureRoboAnimation(
 *   composeRule = composeTestRule,
 *   filePath = "build/outputs/roborazzi/animation.gif",
 *   animationOptions = RoboAnimationOptions(fps = 10),
 * ) {
 *   composeTestRule.onNodeWithTag("toggle").performClick()
 *   delay(300)
 * }
 * ```
 *
 * After [block] returns, recording continues until the UI settles (see
 * [RoboAnimationOptions.settleTimeoutMillis]), so a block that only performs a click still
 * records the whole animation the click starts.
 */
@ExperimentalRoborazziApi
fun SemanticsNodeInteraction.captureRoboAnimation(
  composeRule: ComposeTestRule,
  filePath: String = DefaultFileNameGenerator.generateFilePath("gif"),
  animationOptions: RoboAnimationOptions = RoboAnimationOptions(),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  block: RoboAnimationRecorderScope.() -> Unit
) {
  // currently, animation compare is not supported
  if (!roborazziOptions.taskType.isRecording()) return
  captureRoboAnimation(
    composeRule = composeRule,
    file = fileWithRecordFilePathStrategy(filePath),
    animationOptions = animationOptions,
    roborazziOptions = roborazziOptions,
    block = block
  )
}

@ExperimentalRoborazziApi
fun SemanticsNodeInteraction.captureRoboAnimation(
  composeRule: ComposeTestRule,
  file: File,
  animationOptions: RoboAnimationOptions = RoboAnimationOptions(),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  block: RoboAnimationRecorderScope.() -> Unit
) {
  // currently, animation compare is not supported
  if (!roborazziOptions.taskType.isRecording()) return
  val canvases = mutableListOf<AwtRoboCanvas>()
  val captureFrame = {
    capture(
      rootComponent = RoboComponent.Compose(
        node = fetchSemanticsNode("roborazzi can't find component"),
        roborazziOptions = roborazziOptions
      ),
      roborazziOptions = roborazziOptions
    ) { canvas ->
      canvases.add(canvas)
    }
  }
  val mainClock = composeRule.mainClock
  val wasAutoAdvance = mainClock.autoAdvance
  val result = runCatching {
    composeRule.waitForIdle()
    mainClock.autoAdvance = false
    try {
      captureFrame()
      val scope = RoboAnimationRecorderScope(
        composeRule = composeRule,
        frameStepMillis = animationOptions.frameStepMillis,
        captureFrame = captureFrame
      )
      scope.block()
      recordUntilSettled(composeRule, animationOptions, roborazziOptions, canvases, captureFrame)
    } finally {
      mainClock.autoAdvance = wasAutoAdvance
      composeRule.waitForIdle()
    }
  }
  saveAnimatedGif(file, canvases, animationOptions.fps, roborazziOptions)
  canvases.forEach { it.release() }
  canvases.clear()
  result.getOrThrow()
}

private fun recordUntilSettled(
  composeRule: ComposeTestRule,
  animationOptions: RoboAnimationOptions,
  roborazziOptions: RoborazziOptions,
  canvases: MutableList<AwtRoboCanvas>,
  captureFrame: () -> Unit,
) {
  var settleElapsedMillis = 0L
  while (settleElapsedMillis < animationOptions.settleTimeoutMillis) {
    composeRule.mainClock.advanceTimeBy(animationOptions.frameStepMillis)
    composeRule.waitForIdle()
    captureFrame()
    settleElapsedMillis += animationOptions.frameStepMillis
    val lastCanvas = canvases.getOrNull(canvases.size - 1) ?: return
    val previousCanvas = canvases.getOrNull(canvases.size - 2) ?: return
    val comparisonResult: ImageComparator.ComparisonResult =
      previousCanvas.differ(lastCanvas, 1.0, roborazziOptions.compareOptions.imageComparator)
    if (roborazziOptions.compareOptions.resultValidator(comparisonResult)) {
      // The UI has settled; drop the trailing frame identical to the previous one.
      canvases.removeAt(canvases.size - 1).release()
      return
    }
  }
}

private fun saveAnimatedGif(
  file: File,
  canvases: List<AwtRoboCanvas>,
  fps: Int,
  roborazziOptions: RoborazziOptions,
) {
  file.parentFile?.mkdirs()
  val encoder = AnimatedGifEncoder()
  encoder.setRepeat(0)
  encoder.start(file.outputStream())
  encoder.setFrameRate(fps.toFloat())
  if (canvases.isNotEmpty()) {
    encoder.setSize(
      canvases.maxOf { it.croppedWidth },
      canvases.maxOf { it.croppedHeight }
    )
    canvases.forEach { canvas ->
      encoder.addFrame(canvas, roborazziOptions.recordOptions.resizeScale)
    }
  }
  encoder.finish()
}
