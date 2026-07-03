package com.github.takahirom.roborazzi

/**
 * Marker [ImageIoFormat] for iOS. The iOS canvas ([UIImageRoboCanvas]) always
 * writes PNG via `UIImagePNGRepresentation` and does not consult this value, so
 * the default format is a simple singleton.
 */
@ExperimentalRoborazziApi
object IosImageIoFormat : ImageIoFormat

@ExperimentalRoborazziApi
@Suppress("FunctionName")
actual fun LosslessWebPImageIoFormat(): ImageIoFormat {
  // WebP encoding is not implemented for iOS yet.
  TODO("Lossless WebP output is not supported on iOS")
}

@ExperimentalRoborazziApi
actual fun ImageIoFormat(): ImageIoFormat {
  return IosImageIoFormat
}
