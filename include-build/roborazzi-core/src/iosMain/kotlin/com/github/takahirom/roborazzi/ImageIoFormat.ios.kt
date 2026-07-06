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
  // iOS system frameworks can decode WebP (iOS 14+) but cannot encode it:
  // ImageIO's CGImageDestination exposes no WebP writer on iOS (WebP encoding
  // was only added to ImageIO on macOS), and bundling libwebp is out of scope.
  // Fail with a clear message instead of a bare TODO.
  error("WebP encoding is not supported on iOS; use PNG (the default ImageIoFormat()).")
}

@ExperimentalRoborazziApi
actual fun ImageIoFormat(): ImageIoFormat {
  return IosImageIoFormat
}
