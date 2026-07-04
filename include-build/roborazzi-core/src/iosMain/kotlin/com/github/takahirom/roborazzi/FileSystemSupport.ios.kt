package com.github.takahirom.roborazzi

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo

internal actual fun roborazziToAbsolutePath(path: String): String {
  return resolveRoborazziAbsolutePath(
    path = path,
    projectPath = roborazziSystemPropertyProjectPath(),
    currentDirectory = NSFileManager.defaultManager.currentDirectoryPath,
  )
}

/**
 * Resolves a Roborazzi [path] to an absolute path on iOS.
 *
 * The Gradle plugin passes the output / compare / result directories as
 * project-relative paths together with an absolute `roborazzi.project.path`.
 * Unlike the JVM (whose test process runs with the module as its working
 * directory, so `File(path).absolutePath` is already project-relative), an iOS
 * simulator test's working directory is not the project, so relative paths must
 * be resolved against [projectPath]. When no project path is configured
 * ([roborazziSystemPropertyProjectPath] returns its "." default, e.g. in a
 * non-Gradle run), fall back to the process [currentDirectory].
 */
internal fun resolveRoborazziAbsolutePath(
  path: String,
  projectPath: String,
  currentDirectory: String,
): String {
  if (path.startsWith("/")) return path
  val base = if (projectPath == ".") currentDirectory else projectPath
  return "$base/$path"
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
