package com.github.takahirom.roborazzi

import java.io.File

@InternalRoborazziApi
fun fileWithRecordFilePathStrategy(filePath: String): File {
  return fileWithRecordFilePathStrategy(
    filePath = filePath,
    isWindows = File.separatorChar == '\\'
  )
}

internal fun fileWithRecordFilePathStrategy(filePath: String, isWindows: Boolean): File {
  if (isAbsoluteFilePath(filePath, isWindows)) {
    return File(filePath)
  }
  return when (roborazziRecordFilePathStrategy()) {
    RoborazziRecordFilePathStrategy.RelativePathFromCurrentDirectory -> {
      File(filePath)
    }
    RoborazziRecordFilePathStrategy.RelativePathFromRoborazziContextOutputDirectory -> {
      val outputDirectory = provideRoborazziContext().outputDirectory
      File(outputDirectory, filePath)
    }
  }
}

// Matches a Windows drive-absolute path like "D:\foo" or "D:/foo". A path
// without a separator after the colon ("D:foo") is drive-relative, not absolute.
private val windowsDriveAbsolutePathRegex = Regex("""^[A-Za-z]:[/\\]""")

// The OS is a parameter (instead of using File.isAbsolute) so that Windows
// path handling can be unit-tested on any host OS. On Unix, ':' and '\' are
// valid file name characters, so Windows-looking paths stay relative there.
internal fun isAbsoluteFilePath(filePath: String, isWindows: Boolean): Boolean {
  return if (isWindows) {
    filePath.startsWith("\\\\") || windowsDriveAbsolutePathRegex.containsMatchIn(filePath)
  } else {
    filePath.startsWith("/")
  }
}
