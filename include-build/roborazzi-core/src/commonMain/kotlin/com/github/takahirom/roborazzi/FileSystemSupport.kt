package com.github.takahirom.roborazzi

/**
 * Returns the absolute path for the given path.
 * On JVM/Android this delegates to [java.io.File.getAbsolutePath] to preserve the
 * existing behavior (relative paths are resolved against the current working directory).
 */
internal expect fun roborazziToAbsolutePath(path: String): String

/**
 * Returns whether a file exists at the given path.
 */
internal expect fun roborazziFileExists(path: String): Boolean

/**
 * Creates the directory at the given path (including parent directories) if it does not exist.
 */
internal expect fun roborazziMakeDirectories(path: String)

/**
 * Returns a monotonic timestamp in nanoseconds used for [CaptureResult.timestampNs].
 */
internal expect fun roborazziCurrentTimeNs(): Long
