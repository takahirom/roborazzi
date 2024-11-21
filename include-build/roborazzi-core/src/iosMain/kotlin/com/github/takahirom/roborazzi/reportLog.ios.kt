package com.github.takahirom.roborazzi

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.fprintf
import platform.posix.stderr

@OptIn(ExperimentalForeignApi::class)
actual fun roborazziErrorLog(message: String) {
  fprintf(stderr, "Roborazzi: %s\n", message)
}