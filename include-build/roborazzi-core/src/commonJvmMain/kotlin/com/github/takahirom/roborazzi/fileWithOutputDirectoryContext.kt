package com.github.takahirom.roborazzi

import java.io.File

fun fileWithOutputDirectoryContext(filePath: String): File {
  if (filePath.startsWith("/")) {
    return File(filePath)
  }
  val outputDirectory = provideRoborazziContext().outputDirectory
  val outputDirectoryAbsolutePath = File(outputDirectory).absolutePath
  val filePathAbsolutePath = File(filePath).absolutePath
  if (filePathAbsolutePath.startsWith("$outputDirectoryAbsolutePath/")) {
    // Remove duplicated path
    val prefixRemovedFilePath = filePathAbsolutePath.removePrefix("$outputDirectoryAbsolutePath/")
    val savingFile = File(outputDirectory, prefixRemovedFilePath)
    println(
      "Roborazzi: $filePath is within the output directory. " +
        "Consider using the relative path $prefixRemovedFilePath instead of $filePath. " +
        "This prefix removal is for backward compatibility and may be phased out in future releases. " +
        "If that occurs, your file will be saved at ${File(outputDirectory, filePath).absolutePath}."
    )
    return savingFile
  }
  return File(outputDirectory, filePath)
}

