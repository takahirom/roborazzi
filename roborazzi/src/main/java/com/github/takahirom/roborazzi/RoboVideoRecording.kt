package com.github.takahirom.roborazzi

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import java.io.File
import java.util.concurrent.TimeUnit

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
  recordScreenRoboVideoAfterSetup(
    composeRule = composeRule,
    file = file,
    videoOptions = videoOptions,
    roborazziOptions = roborazziOptions,
    setup = {},
    block = block,
  )
}

/**
 * [recordScreenRoboVideo] with a [setup] that runs with the Compose clock already paused and
 * before the first frame is captured. Use it to install the content to record so it is visible in
 * frame 0. It has its own name (and no default for [setup]) so the shipped
 * [recordScreenRoboVideo] signatures stay untouched.
 */
@InternalRoborazziApi
@OptIn(ExperimentalRoborazziApi::class)
fun recordScreenRoboVideoAfterSetup(
  composeRule: ComposeTestRule,
  file: File,
  videoOptions: RoboVideoOptions,
  roborazziOptions: RoborazziOptions,
  setup: () -> Unit,
  block: RoboVideoRecorderScope.() -> Unit,
) {
  // currently, video compare is not supported
  if (!roborazziOptions.taskType.isRecording()) return
  recordVideo(
    composeRule = composeRule,
    file = file,
    videoOptions = videoOptions,
    roborazziOptions = roborazziOptions,
    setup = setup,
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
 * Adapts a JUnit4 [ComposeTestRule] running under Robolectric to the shared recording loop.
 */
@OptIn(InternalRoborazziApi::class)
private class ComposeRuleRoboVideoClock(private val composeRule: ComposeTestRule) : RoboVideoClock {
  override fun <T> withManualClock(body: () -> T): T {
    val mainClock = composeRule.mainClock
    val wasAutoAdvance = mainClock.autoAdvance
    composeRule.waitForIdle()
    mainClock.autoAdvance = false
    try {
      return body()
    } finally {
      mainClock.autoAdvance = wasAutoAdvance
      composeRule.waitForIdle()
    }
  }

  override fun advanceBy(stepMillis: Long) {
    composeRule.mainClock.advanceTimeBy(stepMillis)
    idleMainLooperFor(stepMillis)
    composeRule.waitForIdle()
  }
}

/**
 * Shared entry for [recordRoboVideo] and [recordScreenRoboVideo]. The only difference between
 * the two is [rootComponentForFrame], which produces the [RoboComponent] to capture for each
 * frame (a single Compose node vs. all screen window roots).
 */
@OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
private fun recordVideo(
  composeRule: ComposeTestRule,
  file: File,
  videoOptions: RoboVideoOptions,
  roborazziOptions: RoborazziOptions,
  setup: () -> Unit = {},
  block: RoboVideoRecorderScope.() -> Unit,
  rootComponentForFrame: () -> RoboComponent,
) {
  recordRoboVideoToFile(
    file = file,
    videoOptions = videoOptions,
    roborazziOptions = roborazziOptions,
    clock = ComposeRuleRoboVideoClock(composeRule),
    setup = setup,
    captureFrame = { onCanvas ->
      capture(
        rootComponent = rootComponentForFrame(),
        roborazziOptions = roborazziOptions,
        onCanvas = onCanvas
      )
    },
    block = block,
  )
}
