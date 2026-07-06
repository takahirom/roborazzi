package com.github.takahirom.roborazzi

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import com.dropbox.differ.ImageComparator
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

/**
 * Options for [recordRoboVideo].
 *
 * @param fps Frames per second of the recorded video. The frame step is 1000 / fps rounded to
 * whole milliseconds ([frameStepMillis]); both the virtual-clock advance per recorded frame and
 * the frame delay encoded in the output are exactly that step, so recording and playback share a
 * single timeline. Note that GIF stores delays in centiseconds, so a step that is not a multiple
 * of 10 ms (e.g. fps = 60 -> 17 ms) is rounded by the GIF format itself; prefer fps values whose
 * step is a multiple of 10 ms (e.g. 10, 20, 25, 50) for exact GIF timing. APNG encodes the step
 * exactly.
 * @param settleTimeoutMillis After [block] finishes, recording continues until the UI stops
 * changing, up to this amount of additional virtual time. This allows capturing animations
 * that are still running when the block ends.
 * @param backgroundColor ARGB color (see [android.graphics.Color]) used to fill the fixed
 * recording viewport. Because Roborazzi crops each frame to its content, frames of a video
 * that changes size have different dimensions; every frame is composited (anchored at the
 * top-left) onto a viewport of the maximum frame size filled with this color, so the output has
 * uniform dimensions with no undefined (black) margins. Defaults to white.
 */
@ExperimentalRoborazziApi
data class RoboVideoOptions(
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

  /**
   * Virtual time advanced per recorded frame: 1000 / [fps] rounded to whole milliseconds (at
   * least 1). This is the single source of truth for timing -- the encoded frame delay is this
   * exact value, so the output plays back at the same speed the frames were captured.
   */
  internal val frameStepMillis: Long get() = (1_000.0 / fps).roundToLong().coerceAtLeast(1)
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
 * Receiver scope of the [recordRoboVideo] block. Interactions performed in the block run
 * with the Compose main clock paused; call [delay] to advance virtual time while recording
 * frames, similar to how a user would watch the UI evolve.
 */
@ExperimentalRoborazziApi
class RoboVideoRecorderScope internal constructor(
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
 * Records this node as a video (an animated image) with a fixed frame rate, so that the
 * output plays back the UI in real time. This is useful for creating recordings of the UI's
 * evolution -- animations, transitions and other time-driven changes -- that designers can review.
 *
 * The output format is chosen by the [filePath]/[file] extension: `.gif` produces a GIF (256
 * colors; the default) and `.png` produces a lossless, full-color APNG (Animated PNG). Prefer
 * `.png` when color fidelity matters. Only these animated-image formats are supported for now;
 * the "video" name was chosen so real video formats (e.g. mp4) can be added without renaming.
 *
 * Unlike [captureRoboGif], which only records visually distinct states with a fixed 1-second
 * delay, this API pauses the Compose main clock and drives it frame by frame while recording,
 * so intermediate animation frames are captured with faithful timing.
 *
 * ```kotlin
 * composeTestRule.onNodeWithTag("box").recordRoboVideo(
 *   composeRule = composeTestRule,
 *   filePath = "build/outputs/roborazzi/video.gif",
 *   videoOptions = RoboVideoOptions(fps = 10),
 * ) {
 *   composeTestRule.onNodeWithTag("toggle").performClick()
 *   delay(300)
 * }
 * ```
 *
 * After [block] returns, recording continues until the UI settles (see
 * [RoboVideoOptions.settleTimeoutMillis]), so a block that only performs a click still
 * records the whole animation the click starts.
 *
 * Note: this API currently only supports recording. When the Roborazzi task is running in
 * compare/verify mode, this function is a complete no-op: [block] is not executed and no image is
 * recorded or verified.
 */
@ExperimentalRoborazziApi
fun SemanticsNodeInteraction.recordRoboVideo(
  composeRule: ComposeTestRule,
  filePath: String = DefaultFileNameGenerator.generateFilePath("gif"),
  videoOptions: RoboVideoOptions = RoboVideoOptions(),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  block: RoboVideoRecorderScope.() -> Unit
) {
  // currently, video compare is not supported
  if (!roborazziOptions.taskType.isRecording()) return
  recordRoboVideo(
    composeRule = composeRule,
    file = fileWithRecordFilePathStrategy(filePath),
    videoOptions = videoOptions,
    roborazziOptions = roborazziOptions,
    block = block
  )
}

@ExperimentalRoborazziApi
fun SemanticsNodeInteraction.recordRoboVideo(
  composeRule: ComposeTestRule,
  file: File,
  videoOptions: RoboVideoOptions = RoboVideoOptions(),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  block: RoboVideoRecorderScope.() -> Unit
) {
  // currently, video compare is not supported
  if (!roborazziOptions.taskType.isRecording()) return
  recordVideo(
    composeRule = composeRule,
    file = file,
    videoOptions = videoOptions,
    roborazziOptions = roborazziOptions,
    block = block,
  ) {
    // Re-fetch the node each frame so the capture reflects the current animation state.
    RoboComponent.Compose(
      node = fetchSemanticsNode("roborazzi can't find component"),
      roborazziOptions = roborazziOptions
    )
  }
}

/**
 * Records the whole screen (all window roots) as a video (an animated image) with a fixed
 * frame rate, so that the output plays back the UI in real time. This is the screen-level
 * counterpart of [recordRoboVideo], mirroring how [captureScreenRoboImage] relates to
 * [captureRoboImage].
 *
 * Prefer this over the node-scoped [recordRoboVideo] for two reasons:
 *
 * 1. **Stable, device-sized viewport.** Every frame captures the entire device screen, so all
 * frames have identical dimensions. This matches what designers expect from a screen recording.
 * A node-scoped recording, by contrast, is cropped to the node, so its dimensions change as the
 * node animates (grows/shrinks).
 * 2. **Captures window overlays.** Overlays drawn at the window root -- such as gesture
 * visualizations (e.g. touch/tap indicators) or dialogs added mid-recording -- live on separate
 * window roots and are invisible to a node-scoped capture. Capturing all window roots per frame
 * includes them.
 *
 * The output format is chosen by the [filePath]/[file] extension: `.gif` produces a GIF (256
 * colors; the default) and `.png` produces a lossless, full-color APNG (Animated PNG). Prefer
 * `.png` when color fidelity matters. Only these animated-image formats are supported for now;
 * the "video" name was chosen so real video formats (e.g. mp4) can be added without renaming.
 *
 * ```kotlin
 * recordScreenRoboVideo(
 *   composeRule = composeTestRule,
 *   filePath = "build/outputs/roborazzi/video.gif",
 *   videoOptions = RoboVideoOptions(fps = 10),
 * ) {
 *   composeTestRule.onNodeWithTag("toggle").performClick()
 *   delay(300)
 * }
 * ```
 *
 * After [block] returns, recording continues until the UI settles (see
 * [RoboVideoOptions.settleTimeoutMillis]), so a block that only performs a click still
 * records the whole animation the click starts.
 *
 * Note: this API currently only supports recording. When the Roborazzi task is running in
 * compare/verify mode, this function is a complete no-op: [block] is not executed and no image is
 * recorded or verified.
 */
@ExperimentalRoborazziApi
fun recordScreenRoboVideo(
  composeRule: ComposeTestRule,
  filePath: String = DefaultFileNameGenerator.generateFilePath("gif"),
  videoOptions: RoboVideoOptions = RoboVideoOptions(),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  block: RoboVideoRecorderScope.() -> Unit
) {
  // currently, video compare is not supported
  if (!roborazziOptions.taskType.isRecording()) return
  recordScreenRoboVideo(
    composeRule = composeRule,
    file = fileWithRecordFilePathStrategy(filePath),
    videoOptions = videoOptions,
    roborazziOptions = roborazziOptions,
    block = block
  )
}

@ExperimentalRoborazziApi
fun recordScreenRoboVideo(
  composeRule: ComposeTestRule,
  file: File,
  videoOptions: RoboVideoOptions = RoboVideoOptions(),
  roborazziOptions: RoborazziOptions = provideRoborazziContext().options,
  block: RoboVideoRecorderScope.() -> Unit
) {
  // currently, video compare is not supported
  if (!roborazziOptions.taskType.isRecording()) return
  recordVideo(
    composeRule = composeRule,
    file = file,
    videoOptions = videoOptions,
    roborazziOptions = roborazziOptions,
    block = block,
  ) {
    // Idle the main Looper so windows added mid-recording (e.g. dialogs, or a gesture overlay
    // attached on a posted message) are laid out before we enumerate the roots. The recorder loop
    // already calls composeRule.waitForIdle() each step; this drains the pending Looper messages
    // that create/lay out those windows. (We deliberately avoid Espresso.onIdle() here, which can
    // interact badly with the paused Compose clock.)
    idleMainLooperFor(0)
    // Re-fetch the window roots each frame so windows added mid-recording (e.g. dialogs) are
    // included in the capture.
    RoboComponent.Screen(
      rootsOrderByDepth = fetchRobolectricWindowRoots(),
      roborazziOptions = roborazziOptions
    )
  }
}

/**
 * Shared recording loop for [recordRoboVideo] and [recordScreenRoboVideo]. The only
 * difference between the two is [rootComponentForFrame], which produces the [RoboComponent] to
 * capture for each frame (a single Compose node vs. all screen window roots).
 */
private fun recordVideo(
  composeRule: ComposeTestRule,
  file: File,
  videoOptions: RoboVideoOptions,
  roborazziOptions: RoborazziOptions,
  block: RoboVideoRecorderScope.() -> Unit,
  rootComponentForFrame: () -> RoboComponent,
) {
  val canvases = mutableListOf<AwtRoboCanvas>()
  val captureFrame = {
    capture(
      rootComponent = rootComponentForFrame(),
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
      val scope = RoboVideoRecorderScope(
        composeRule = composeRule,
        frameStepMillis = videoOptions.frameStepMillis,
        captureFrame = captureFrame
      )
      scope.block()
      recordUntilSettled(composeRule, videoOptions, roborazziOptions, canvases, captureFrame)
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
      runCatching { saveAnimatedImage(file, canvases, videoOptions, roborazziOptions) }
        .exceptionOrNull()?.let { failure.addSuppressed(it) }
      throw failure
    }
    // The block succeeded, so a save failure is a real failure and should surface normally.
    saveAnimatedImage(file, canvases, videoOptions, roborazziOptions)
  } finally {
    // Release canvases even if saving throws so failures don't leak AwtRoboCanvas instances.
    canvases.forEach { it.release() }
    canvases.clear()
  }
}

private fun recordUntilSettled(
  composeRule: ComposeTestRule,
  videoOptions: RoboVideoOptions,
  roborazziOptions: RoborazziOptions,
  canvases: MutableList<AwtRoboCanvas>,
  captureFrame: () -> Unit,
) {
  var settleElapsedMillis = 0L
  while (settleElapsedMillis < videoOptions.settleTimeoutMillis) {
    composeRule.mainClock.advanceTimeBy(videoOptions.frameStepMillis)
    idleMainLooperFor(videoOptions.frameStepMillis)
    composeRule.waitForIdle()
    captureFrame()
    settleElapsedMillis += videoOptions.frameStepMillis
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

@OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
private fun saveAnimatedImage(
  file: File,
  canvases: List<AwtRoboCanvas>,
  videoOptions: RoboVideoOptions,
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
    // The encoded frame delay is exactly the virtual-clock step used while recording, so recording
    // and playback share a single timeline (see RoboVideoOptions.frameStepMillis).
    encoder.setFrameDelayMillis(videoOptions.frameStepMillis)
    if (canvases.isNotEmpty()) {
      val resizeScale = roborazziOptions.recordOptions.resizeScale
      encoder.setSize(
        canvases.maxOf { scaledDimension(it.croppedWidth, resizeScale) },
        canvases.maxOf { scaledDimension(it.croppedHeight, resizeScale) }
      )
      encoder.setBackground(videoOptions.backgroundColor)
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
 * Roborazzi crops each frame to its content, so frames of a video that changes size have
 * different dimensions. The maximum scaled dimension across all frames pins a constant viewport
 * filled with the background color; the encoder composites every frame onto it (anchored
 * top-left) so all encoded frames have identical dimensions and leave no undefined area that
 * decoders would otherwise render as black margins. The result is coerced to at least 1 (matching
 * the truncation in [AwtRoboCanvas]'s scaling) so a tiny frame with a small [resizeScale] never
 * yields a zero-sized, invalid viewport.
 */
private fun scaledDimension(value: Int, resizeScale: Double): Int =
  (if (resizeScale == 1.0) value else (value * resizeScale).toInt()).coerceAtLeast(1)
