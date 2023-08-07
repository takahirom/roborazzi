package com.github.takahirom.roborazzi

import com.dropbox.differ.ImageComparator
import kotlin.math.roundToInt

/**
 * From: Dropshots https://github.com/dropbox/dropshots/blob/978668a7417b879baa9160b3625fd94f476281a3/dropshots/src/main/java/com/dropbox/dropshots/ResultValidator.kt#L26
 * Fails validation if more than `threshold` percent of pixels are different.
 */
@Suppress("FunctionName")
public fun ThresholdValidator(threshold: Float) : (ImageComparator.ComparisonResult) -> Boolean {
  require(threshold in 0f..1f) { "threshold must be in range 0.0..1.0"}
  return { result ->
    result.pixelDifferences <= (result.pixelCount * threshold).roundToInt()
  }
}