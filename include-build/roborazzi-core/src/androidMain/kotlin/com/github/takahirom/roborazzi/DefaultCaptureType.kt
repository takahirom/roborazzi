package com.github.takahirom.roborazzi

actual fun defaultCaptureType(): RoborazziOptions.CaptureType {
  return Dump()
}