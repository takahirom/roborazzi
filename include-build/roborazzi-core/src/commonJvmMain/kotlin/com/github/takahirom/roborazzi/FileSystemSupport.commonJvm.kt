package com.github.takahirom.roborazzi

import java.io.File

internal actual fun roborazziToAbsolutePath(path: String): String {
  return File(path).absolutePath
}

internal actual fun roborazziFileExists(path: String): Boolean {
  return File(path).exists()
}

internal actual fun roborazziMakeDirectories(path: String) {
  File(path).mkdirs()
}

internal actual fun roborazziCurrentTimeNs(): Long {
  return System.nanoTime()
}
