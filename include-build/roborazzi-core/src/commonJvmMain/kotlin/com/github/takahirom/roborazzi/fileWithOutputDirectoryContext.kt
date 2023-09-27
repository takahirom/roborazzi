package com.github.takahirom.roborazzi

import java.io.File

@InternalRoborazziApi
fun fileWithOutputDirectoryContext(filePath: String): File {
  if (filePath.startsWith("/")) {
    return File(filePath)
  }
  when (roborazziCaptureRoboImageFilePathStrategy()) {
    RoborazziCaptureRoboImageFilePathStrategy.RelativePathFromCurrentDirectory -> {
      return File(filePath)
    }
    RoborazziCaptureRoboImageFilePathStrategy.RelativePathFromRoborazziContextOutputDirectory -> {
      val outputDirectory = provideRoborazziContext().outputDirectory
      return File(outputDirectory, filePath)
    }
  }
}

