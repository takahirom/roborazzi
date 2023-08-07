package com.github.takahirom.roborazzi

import jdk.jshell.spi.ExecutionControl.NotImplementedException

actual fun defaultCaptureType(): RoborazziOptions.CaptureType {
  return throw NotImplementedException("defaultCaptureType(would be dump) is not implemented on desktop")
}