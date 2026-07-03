package com.github.takahirom.roborazzi

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo

internal actual fun roborazziToAbsolutePath(path: String): String {
  if (path.startsWith("/")) return path
  val currentDirectory = NSFileManager.defaultManager.currentDirectoryPath
  return "$currentDirectory/$path"
}

internal actual fun roborazziFileExists(path: String): Boolean {
  return SystemFileSystem.exists(Path(path))
}

internal actual fun roborazziMakeDirectories(path: String) {
  SystemFileSystem.createDirectories(Path(path))
}

internal actual fun roborazziCurrentTimeNs(): Long {
  // systemUptime is monotonic seconds since boot; convert to nanoseconds.
  return (NSProcessInfo.processInfo.systemUptime * 1_000_000_000L).toLong()
}
