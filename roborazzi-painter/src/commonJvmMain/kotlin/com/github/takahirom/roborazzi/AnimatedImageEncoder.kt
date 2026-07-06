package com.github.takahirom.roborazzi

import java.io.File
import java.io.OutputStream

/**
 * Abstraction over animated-image encoders used by the animation capture APIs. This is an
 * internal contract between the roborazzi and roborazzi-painter modules and is subject to change
 * without notice. The concrete encoder is chosen by output file extension via
 * [animatedImageEncoderFor]:
 *
 * - `.gif` -> [GifAnimatedImageEncoder] (256-color, backed by [AnimatedGifEncoder])
 * - `.png` -> [AnimatedPngEncoder] (lossless full-color APNG)
 *
 * Usage mirrors the legacy [AnimatedGifEncoder] flow: [start], configuration, one or more
 * [addFrame], then [finish]. Frames are composited (anchored at the top-left) onto a fixed
 * viewport of [setSize] filled with the [setBackground] color so every encoded frame has
 * identical dimensions and leaves no undefined (black) margins.
 */
@InternalRoborazziApi
interface AnimatedImageEncoder {
  fun start(output: OutputStream)
  fun setRepeat(iter: Int)

  /**
   * Sets the delay between frames in milliseconds. The caller's capture step is passed verbatim so
   * that the encoded timeline matches the recorded one; how exactly the delay can be represented
   * is format-specific (APNG stores it exactly, GIF rounds to centiseconds).
   */
  fun setFrameDelayMillis(millis: Long)
  fun setBackground(argb: Int?)
  fun setSize(width: Int, height: Int)
  fun addFrame(canvas: AwtRoboCanvas, resizeScale: Double)
  fun finish()
}

/**
 * Returns the [AnimatedImageEncoder] matching [file]'s extension. Defaults to GIF for unknown
 * extensions to preserve the historical behavior.
 */
@InternalRoborazziApi
fun animatedImageEncoderFor(file: File): AnimatedImageEncoder =
  when (file.extension.lowercase()) {
    "png" -> AnimatedPngEncoder()
    else -> GifAnimatedImageEncoder()
  }

/**
 * Thin adapter over the existing [AnimatedGifEncoder]. [AnimatedGifEncoder] keeps its own public
 * API for legacy callers. The encoder's boolean error returns are surfaced here as
 * [IllegalStateException] so failures are not silently swallowed.
 *
 * Note on timing: GIF stores frame delays in centiseconds, so [setFrameDelayMillis] is rounded to
 * the nearest 10 ms by [AnimatedGifEncoder.setDelay]. A delay that is not a multiple of 10 ms
 * cannot be represented exactly -- e.g. an fps of 60 (17 ms step) actually plays at 20 ms/frame
 * (50 fps). Prefer fps values whose frame step is a multiple of 10 ms (e.g. 10, 20, 25, 50) for
 * exact GIF timing; APNG does not have this limitation.
 */
@OptIn(InternalRoborazziApi::class)
internal class GifAnimatedImageEncoder : AnimatedImageEncoder {
  private val encoder = AnimatedGifEncoder()
  override fun start(output: OutputStream) {
    check(encoder.start(output)) { "Failed to start GIF encoding" }
  }

  override fun setRepeat(iter: Int) {
    encoder.setRepeat(iter)
  }

  override fun setFrameDelayMillis(millis: Long) {
    // AnimatedGifEncoder.setDelay rounds to GIF's centisecond resolution (see the class KDoc).
    encoder.setDelay(millis.toInt())
  }

  override fun setBackground(argb: Int?) {
    encoder.setBackground(argb)
  }

  override fun setSize(width: Int, height: Int) {
    encoder.setSize(width, height)
  }

  override fun addFrame(canvas: AwtRoboCanvas, resizeScale: Double) {
    check(encoder.addFrame(canvas, resizeScale)) { "Failed to add a frame to the GIF" }
  }

  override fun finish() {
    check(encoder.finish()) { "Failed to finish GIF encoding" }
  }
}
