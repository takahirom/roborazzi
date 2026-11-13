package com.github.takahirom.roborazzi

/**
 * Calculates the scale for a captured image from its size, so that one setting can keep small
 * screenshots sharp and large ones small on disk.
 *
 * Set it with [RoborazziOptions.RecordOptions.resizeScaleCalculator]. It replaces
 * [RoborazziOptions.RecordOptions.resizeScale] for every image. An image of the same size gets the
 * same scale when the golden is recorded and when it is compared. A different capture size can map
 * to the same scaled size; the scaled images are then compared pixel by pixel as usual.
 *
 * Keep it a pure function of the size: Roborazzi may call it more than once per capture. A GIF or
 * video gets one scale for all frames, calculated from the widest and the tallest frame. The UI
 * tree dump calculates its scale from the size of the captured tree, which matches the image
 * unless the image is cropped.
 *
 * ```kotlin
 * RoborazziOptions.RecordOptions(
 *   resizeScaleCalculator = { size -> if (size.width * size.height > 1_000_000) 0.5 else 1.0 }
 * )
 * ```
 */
@ExperimentalRoborazziApi
fun interface ResizeScaleCalculator {

  /**
   * The size of the captured image in pixels, after cropping and before scaling.
   */
  @Poko
  class Size(val width: Int, val height: Int)

  /**
   * Returns the scale for an image of [size]. It must be a finite number greater than 0.
   */
  fun calculate(size: Size): Double
}

/**
 * Returns the scale for a captured image of [width] x [height] pixels: the result of
 * [RoborazziOptions.RecordOptions.resizeScaleCalculator] when it is set, otherwise
 * [RoborazziOptions.RecordOptions.resizeScale].
 */
@OptIn(ExperimentalRoborazziApi::class)
@InternalRoborazziApi
fun RoborazziOptions.RecordOptions.resizeScaleFor(width: Int, height: Int): Double {
  val calculator = resizeScaleCalculator ?: return resizeScale
  val scale = calculator.calculate(ResizeScaleCalculator.Size(width = width, height = height))
  require(scale.isFinite() && scale > 0.0) {
    "resizeScaleCalculator returned $scale for an image of ${width}x$height. " +
      "The scale must be a finite number greater than 0."
  }
  return scale
}
