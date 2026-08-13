package com.github.takahirom.roborazzi

/**
 * iOS has no JUnit Platform reporting bridge, so publishing a captured image is a no-op.
 */
internal actual fun roborazziReportCapturedImage(absolutePath: String) {
  // No-op: there is no reporting bridge on iOS.
}
