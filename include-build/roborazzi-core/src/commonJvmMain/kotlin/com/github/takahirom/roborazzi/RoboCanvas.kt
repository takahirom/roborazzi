package com.github.takahirom.roborazzi

import com.dropbox.differ.ImageComparator
import java.io.File

interface RoboCanvas {
  val croppedWidth: Int
  val croppedHeight: Int
  val width: Int
  val height: Int
  fun save(
    file: File,
    resizeScale: Double,
    contextData: Map<String, Any>,
  )
  fun differ(other: RoboCanvas, resizeScale: Double, imageComparator: ImageComparator): ImageComparator.ComparisonResult
  fun release()

}

