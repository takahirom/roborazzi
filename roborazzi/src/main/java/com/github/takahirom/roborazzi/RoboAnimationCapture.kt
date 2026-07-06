package com.github.takahirom.roborazzi

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import com.dropbox.differ.ImageComparator
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Options for [captureRoboAnimation].
 *
 * @param fps Frames per second of the recorded animation. The recording clock is advanced by
 * 1000 / fps milliseconds of virtual time per frame, so the output plays back in real time.
 * @param settleTimeoutMillis After [block] finishes, recording continues until the UI stops
 * changing, up to this amount of additional virtual time. This allows capturing animations
 * that are still running when the block ends.
 * @param backgroundColor ARGB color (see [android.graphics.Color]) used to fill the fixed
 * recording viewport. Because Roborazzi crops each frame to its content, frames of an animation
 * that changes size have different dimensions; every frame is composited (anchored at the
 * top-left) onto a viewport of the maximum frame size filled with this color, so the output has
 * uniform dimensions with no undefined (black) margins. Defaults to white.
 */
@ExperimentalRoborazziApi
data class RoboAnimationOptions(
  val fps: Int = 10,
  val settleTimeoutMillis: Long = 3_000,
  val backgroundColor: Int = android.graphics.Color.WHITE,
) {
  init {
    require(fps in 1..100) { "fps must be in 1..100 but was $fps" }
    require(settleTimeoutMillis >= 0) {
      "settleTimeoutMillis must be >= 0 but was $settleTimeoutMillis"
    }
  }

  val frameStepMillis: Long get() = 1_000L / fps
}

/**
 * Set once the Robolectric [org.robolectric.shadows.ShadowLooper] class is found to be missing so
 * that [idleMainLooperFor] stops attempting (and logging) on every subsequent frame.
 */
private var mainLooperIdlingUnavailable = false

/**
 * Advances the Robolectric main Looper's virtual clock by [stepMillis] in lockstep with the
 * Compose main clock. Coroutine `delay()` calls made from a `LaunchedEffect` (e.g. suspend-based
 * input-gesture drivers) are scheduled on the AndroidUiDispatcher, which is backed by the main
 * Looper's message queue. Under Robolectric's PAUSED looper those delayed messages only run when
 * the Looper's clock advances, and [androidx.compose.ui.test.MainTestClock.advanceTimeBy] does not
 * advance it. Idling the Looper here lets such coroutines make progress while frames are recorded.
 *
 * No-op when Robolectric is not on the classpath; the failure is logged once (via
 * [roborazziDebugLog]) and no further attempts are made.
 */
private fun idleMainLooperFor(stepMillis: Long) {
  if (mainLooperIdlingUnavailable) return
  try {
    org.robolectric.shadows.ShadowLooper.shadowMainLooper()
      .idleFor(stepMillis, TimeUnit.MILLISECONDS)
  } catch (e: NoClassDefFoundError) {
    mainLooperIdlingUnavailable = true
    roborazziDebugLog {
      "Robolectric ShadowLooper is unavailable; skipping main looper idling while recording: $e"
    }
  }
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
      idleMainLooperFor(stepMillis)
      composeRule.waitForIdle()
      captureFrame()
      remainingMillis -= stepMillis
    }
  }
}

/**
 * Records the animation of this node as an animated image with a fixed frame rate, so that the
 * output plays back the UI in real time. This is useful for creating videos of animations that
 * designers can review.
 *
 * The output format is chosen by the [filePath]/[file] extension: `.gif` produces a GIF (256
 * colors; the default) and `.png` produces a lossless, full-color APNG (Animated PNG). Prefer
 * `.png` when color fidelity matters.
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
  try {
    val failure = result.exceptionOrNull()
    if (failure != null) {
      // The block failed. Attempt a best-effort save so any frames captured before the failure
      // are still written, but never let a save failure mask the original one: attach it as a
      // suppressed exception and always rethrow the original first-class failure.
      runCatching { saveAnimatedImage(file, canvases, animationOptions, roborazziOptions) }
        .exceptionOrNull()?.let { failure.addSuppressed(it) }
      throw failure
    }
    // The block succeeded, so a save failure is a real failure and should surface normally.
    saveAnimatedImage(file, canvases, animationOptions, roborazziOptions)
  } finally {
    // Release canvases even if saving throws so failures don't leak AwtRoboCanvas instances.
    canvases.forEach { it.release() }
    canvases.clear()
  }
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
    idleMainLooperFor(animationOptions.frameStepMillis)
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

@OptIn(ExperimentalRoborazziApi::class)
private fun saveAnimatedImage(
  file: File,
  canvases: List<AwtRoboCanvas>,
  animationOptions: RoboAnimationOptions,
  roborazziOptions: RoborazziOptions,
) {
  file.parentFile?.mkdirs()
  // Pick the encoder from the file extension: .gif -> GIF (256 colors), .png -> lossless APNG.
  val encoder = animatedImageEncoderFor(file)
  encoder.setRepeat(0)
  // The encoders flush but do not close a stream passed to start(), so close it here (after
  // finish()) to avoid leaking the file handle. Encoders throw on an internal failure (e.g. the
  // GIF encoder's boolean error returns are surfaced as IllegalStateException in its adapter).
  file.outputStream().use { outputStream ->
    encoder.start(outputStream)
    encoder.setFrameRate(animationOptions.fps.toFloat())
    if (canvases.isNotEmpty()) {
      val resizeScale = roborazziOptions.recordOptions.resizeScale
      encoder.setSize(
        canvases.maxOf { scaledDimension(it.croppedWidth, resizeScale) },
        canvases.maxOf { scaledDimension(it.croppedHeight, resizeScale) }
      )
      encoder.setBackground(animationOptions.backgroundColor)
      canvases.forEach { canvas ->
        encoder.addFrame(canvas, resizeScale)
      }
    }
    encoder.finish()
  }
}

/**
 * Scales a frame dimension by [resizeScale] for the fixed recording viewport.
 *
 * Roborazzi crops each frame to its content, so frames of an animation that changes size have
 * different dimensions. The maximum scaled dimension across all frames pins a constant viewport
 * filled with the background color; the encoder composites every frame onto it (anchored
 * top-left) so all encoded frames have identical dimensions and leave no undefined area that
 * decoders would otherwise render as black margins. The result is coerced to at least 1 (matching
 * the truncation in [AwtRoboCanvas]'s scaling) so a tiny frame with a small [resizeScale] never
 * yields a zero-sized, invalid viewport.
 */
private fun scaledDimension(value: Int, resizeScale: Double): Int =
  (if (resizeScale == 1.0) value else (value * resizeScale).toInt()).coerceAtLeast(1)
