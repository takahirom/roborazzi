package com.github.takahirom.roborazzi

import java.io.File
import java.io.OutputStream

/**
 * Abstraction over animated-image encoders used by the animation capture APIs. The concrete
 * encoder is chosen by output file extension via [animatedImageEncoderFor]:
 *
 * - `.gif` -> [GifAnimatedImageEncoder] (256-color, backed by [AnimatedGifEncoder])
 * - `.png` -> [AnimatedPngEncoder] (lossless full-color APNG)
 *
 * Usage mirrors the legacy [AnimatedGifEncoder] flow: [start], configuration, one or more
 * [addFrame], then [finish]. Frames are composited (anchored at the top-left) onto a fixed
 * viewport of [setSize] filled with the [setBackground] color so every encoded frame has
 * identical dimensions and leaves no undefined (black) margins.
 */
@ExperimentalRoborazziApi
interface AnimatedImageEncoder {
  fun start(output: OutputStream)
  fun setRepeat(iter: Int)
  fun setFrameRate(fps: Float)
  fun setBackground(argb: Int?)
  fun setSize(width: Int, height: Int)
  fun addFrame(canvas: AwtRoboCanvas, resizeScale: Double)
  fun finish()
}

/**
 * Returns the [AnimatedImageEncoder] matching [file]'s extension. Defaults to GIF for unknown
 * extensions to preserve the historical behavior.
 */
@ExperimentalRoborazziApi
fun animatedImageEncoderFor(file: File): AnimatedImageEncoder =
  when (file.extension.lowercase()) {
    "png" -> AnimatedPngEncoder()
    else -> GifAnimatedImageEncoder()
  }

/**
 * Thin adapter over the existing [AnimatedGifEncoder] so the GIF output path is byte-for-byte
 * unchanged. [AnimatedGifEncoder] keeps its own public API for legacy callers. The encoder's
 * boolean error returns are surfaced here as [IllegalStateException] so failures are not silently
 * swallowed.
 */
@OptIn(ExperimentalRoborazziApi::class)
internal class GifAnimatedImageEncoder : AnimatedImageEncoder {
  private val encoder = AnimatedGifEncoder()
  override fun start(output: OutputStream) {
    check(encoder.start(output)) { "Failed to start GIF encoding" }
  }

  override fun setRepeat(iter: Int) {
    encoder.setRepeat(iter)
  }

  override fun setFrameRate(fps: Float) {
    encoder.setFrameRate(fps)
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
