package com.github.takahirom.roborazzi

@InternalRoborazziApi
inline fun roborazziDebugLog(crossinline message: () -> String) {
  if (ROBORAZZI_DEBUG) {
    println("Roborazzi Debug: ${message()}")
  }
}

// TODO: Remove this after 2025-08-01
@Deprecated(
  message = "This function is deprecated because we do not intend for users to use it. The previous name was misleading and it has been renamed to roborazziDebugLog.",
  replaceWith = ReplaceWith("roborazziDebugLog(message)")
)
inline fun debugLog(crossinline message: () -> String) {
  roborazziDebugLog(message)
}
