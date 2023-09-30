package com.github.takahirom.roborazzi

import java.io.File

@InternalRoborazziApi
fun fileWithRecordFilePathStrategy(filePath: String): File {
  if (filePath.startsWith("/")) {
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

