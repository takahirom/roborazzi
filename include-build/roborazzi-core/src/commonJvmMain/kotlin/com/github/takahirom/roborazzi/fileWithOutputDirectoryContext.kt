package com.github.takahirom.roborazzi

import java.io.File

fun fileWithOutputDirectoryContext(filePath: String): File {
  if (filePath.startsWith("/")) {
    return File(filePath)
  }
  when (roborazziCaptureRoboImageFilePathStrategy()) {
    RoborazziCaptureRoboImageFilePathStrategy.RelativePathFromCurrentDirectoryCaptureRoboImage -> {
      return File(filePath)
    }
    RoborazziCaptureRoboImageFilePathStrategy.RelativePathFromRoborazziContextOutputDirectoryCaptureRoboImage -> {
      val outputDirectory = provideRoborazziContext().outputDirectory
      return File(outputDirectory, filePath)
    }
  }
}

