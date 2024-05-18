package com.github.takahirom.roborazzi

import com.dropbox.differ.ImageComparator
import kotlinx.io.files.Path

interface RoboCanvas2 {
  val croppedWidth: Int
  val croppedHeight: Int
  val width: Int
  val height: Int
  fun save(
    file: Path,
    resizeScale: Double,
    contextData: Map<String, Any>,
  )
  fun differ(other: RoboCanvas2, resizeScale: Double, imageComparator: ImageComparator): ImageComparator.ComparisonResult
  fun release()

}

