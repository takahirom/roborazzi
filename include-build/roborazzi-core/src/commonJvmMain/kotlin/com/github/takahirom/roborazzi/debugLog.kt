package com.github.takahirom.roborazzi

inline fun debugLog(crossinline message: () -> String) {
  if (ROBORAZZI_DEBUG) {
    println("Roborazzi Debug: ${message()}")
  }
}
