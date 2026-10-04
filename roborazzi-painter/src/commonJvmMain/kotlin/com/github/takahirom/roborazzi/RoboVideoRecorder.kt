package com.github.takahirom.roborazzi

import com.dropbox.differ.ImageComparator
import java.io.File

/**
 * Platform adapter that lets the shared recording loop drive a Compose test clock without
 * depending on a specific test harness (a JUnit4 `ComposeTestRule` on Android, `ComposeUiTest`
 * on Compose Desktop).
 */
@InternalRoborazziApi
interface RoboVideoClock {
  /**
   * Runs [body] with the Compose main clock paused (auto advance off) after the UI is idle, and
   * restores the previous clock mode and waits for idle again afterwards, even when [body] throws.
   */
  fun <T> withManualClock(body: () -> T): T

  /**
   * Advances virtual time by [stepMillis] and lets everything that depends on it run, so that the
   * next captured frame reflects the new time.
   */
  fun advanceBy(stepMillis: Long)
}

/**
 * Shared recording loop for `recordRoboVideo` / `recordScreenRoboVideo` on every platform.
 * [captureFrame] captures one frame and hands the resulting canvas to its callback.
 */
@OptIn(ExperimentalRoborazziApi::class)
@InternalRoborazziApi
fun recordRoboVideoToFile(
  file: File,
  videoOptions: RoboVideoOptions,
  roborazziOptions: RoborazziOptions,
  clock: RoboVideoClock,
  captureFrame: (onCanvas: (AwtRoboCanvas) -> Unit) -> Unit,
  setup: () -> Unit = {},
  block: RoboVideoRecorderScope.() -> Unit,
) {
  val canvases = mutableListOf<AwtRoboCanvas>()
  val captureFrameIntoList = { captureFrame { canvas -> canvases.add(canvas) } }
  val result = runCatching {
    clock.withManualClock {
      // Runs with the clock paused and before the first frame, so what it installs (e.g. the
      // content under test) is already visible in frame 0 and its entrance animations are recorded.
      setup()
      captureFrameIntoList()
      val scope = RoboVideoRecorderScope(
        frameStepMillis = videoOptions.frameStepMillis,
        advanceClock = clock::advanceBy,
        captureFrame = captureFrameIntoList
      )
      scope.block()
      recordUntilSettled(clock, videoOptions, roborazziOptions, canvases, captureFrameIntoList)
    }
  }
  try {
    val failure = result.exceptionOrNull()
    if (failure != null) {
      // The block failed. Attempt a best-effort save so any frames captured before the failure
      // are still written, but never let a save failure mask the original one: attach it as a
      // suppressed exception and always rethrow the original first-class failure. Skip the save
      // entirely when no frame was captured (e.g. the very first capture failed) so a failed
      // recording never leaves an empty, invalid animation file behind.
      if (canvases.isNotEmpty()) {
        runCatching { saveAnimatedImage(file, canvases, videoOptions, roborazziOptions) }
          .exceptionOrNull()?.let { failure.addSuppressed(it) }
      }
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
  clock: RoboVideoClock,
  videoOptions: RoboVideoOptions,
  roborazziOptions: RoborazziOptions,
  canvases: MutableList<AwtRoboCanvas>,
  captureFrame: () -> Unit,
) {
  var settleElapsedMillis = 0L
  while (settleElapsedMillis < videoOptions.settleTimeoutMillis) {
    // Clamp the final step to the remaining budget (mirroring RoboVideoRecorderScope.delay) so
    // the settle phase never advances virtual time past settleTimeoutMillis.
    val stepMillis =
      minOf(videoOptions.frameStepMillis, videoOptions.settleTimeoutMillis - settleElapsedMillis)
    clock.advanceBy(stepMillis)
    captureFrame()
    settleElapsedMillis += stepMillis
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
