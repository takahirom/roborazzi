package com.github.takahirom.roborazzi

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
 * @param backgroundColor ARGB color (for example `0xFFFFFFFF.toInt()` for opaque white) used to fill the fixed
 * recording viewport. Because Roborazzi crops each frame to its content, frames of a video
 * that changes size have different dimensions; every frame is composited (anchored at the
 * top-left) onto a viewport of the maximum frame size filled with this color, so the output has
 * uniform dimensions with no undefined (black) margins. Defaults to white.
 */
@ExperimentalRoborazziApi
data class RoboVideoOptions(
  val fps: Int = 10,
  val settleTimeoutMillis: Long = 3_000,
  val backgroundColor: Int = 0xFFFFFFFF.toInt(),
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
  @InternalRoborazziApi
  val frameStepMillis: Long get() = (1_000.0 / fps).roundToLong().coerceAtLeast(1)
}

/**
 * Receiver scope of the `recordRoboVideo` block. Interactions performed in the block run
 * with the Compose main clock paused; call [delay] to advance virtual time while recording
 * frames, similar to how a user would watch the UI evolve.
 */
@ExperimentalRoborazziApi
class RoboVideoRecorderScope @InternalRoborazziApi constructor(
  private val frameStepMillis: Long,
  /** Advances virtual time by the given step and lets the UI reflect it. */
  private val advanceClock: (stepMillis: Long) -> Unit,
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
      advanceClock(stepMillis)
      captureFrame()
      remainingMillis -= stepMillis
    }
  }
}
