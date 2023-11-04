package com.github.takahirom.roborazzi

import com.dropbox.differ.ImageComparator
import com.github.takahirom.roborazzi.hash.ImageHashCalculator
import java.io.File

interface RoboCanvas {
  val croppedWidth: Int
  val croppedHeight: Int
  val width: Int
  val height: Int
  fun save(file: File, resizeScale: Double, imageHashCalculator: ImageHashCalculator?)

  fun hash(imageHashCalculator: ImageHashCalculator, resizeScale: Double): ImageHashCalculator.HashResult
  fun differ(other: RoboCanvas, resizeScale: Double, imageComparator: ImageComparator): ImageComparator.ComparisonResult
  fun release()

}

