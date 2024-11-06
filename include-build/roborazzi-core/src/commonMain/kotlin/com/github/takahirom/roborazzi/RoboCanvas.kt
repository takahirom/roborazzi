package com.github.takahirom.roborazzi

import com.dropbox.differ.ImageComparator

interface RoboCanvas {
  val croppedWidth: Int
  val croppedHeight: Int
  val width: Int
  val height: Int
  fun save(
    path: String,
    resizeScale: Double,
    contextData: Map<String, Any>,
    imageIoFormat: ImageIoFormat,
  )
  fun differ(other: RoboCanvas, resizeScale: Double, imageComparator: ImageComparator): ImageComparator.ComparisonResult
  fun release()
}

