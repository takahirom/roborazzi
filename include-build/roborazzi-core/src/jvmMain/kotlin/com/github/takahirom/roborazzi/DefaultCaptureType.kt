package com.github.takahirom.roborazzi

actual fun defaultCaptureType(): RoborazziOptions.CaptureType {
  throw NotImplementedError("defaultCaptureType(would be dump) is not implemented on desktop")
}