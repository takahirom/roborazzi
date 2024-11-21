package com.github.takahirom.roborazzi

actual fun roborazziErrorLog(message: String) {
  System.err.println("Roborazzi: $message")
}