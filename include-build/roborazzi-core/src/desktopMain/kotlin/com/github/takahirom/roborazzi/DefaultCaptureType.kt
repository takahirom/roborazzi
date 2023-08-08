package com.github.takahirom.roborazzi

actual fun defaultCaptureType(): RoborazziOptions.CaptureType {
  return throw NotImplementedError("defaultCaptureType(would be dump) is not implemented on desktop")
}