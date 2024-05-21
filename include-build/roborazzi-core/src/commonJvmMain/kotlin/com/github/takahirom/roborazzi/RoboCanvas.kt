package com.github.takahirom.roborazzi

import com.dropbox.differ.ImageComparator
import kotlinx.io.files.Path

interface RoboCanvas {
  val croppedWidth: Int
  val croppedHeight: Int
  val width: Int
  val height: Int
  fun save(
    path: Path,
    resizeScale: Double,
    contextData: Map<String, Any>,
  )
  fun differ(other: RoboCanvas, resizeScale: Double, imageComparator: ImageComparator): ImageComparator.ComparisonResult
  fun release()

}

