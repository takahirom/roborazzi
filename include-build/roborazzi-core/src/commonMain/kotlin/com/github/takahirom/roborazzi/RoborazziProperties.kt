package com.github.takahirom.roborazzi

const val DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH: String = "build/outputs/roborazzi"
var ROBORAZZI_DEBUG: Boolean = false

@ExperimentalRoborazziApi
fun roborazziSystemPropertyOutputDirectory(): String {
  return getSystemProperty("roborazzi.output.dir", DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH)
}

@ExperimentalRoborazziApi
fun roborazziSystemPropertyResultDirectory(): String {
  return getSystemProperty("roborazzi.result.dir",
    "build/${RoborazziReportConst.resultDirPathFromBuildDir}"
  )
}

@ExperimentalRoborazziApi
// This will be removed when we found if this is safe.
fun roborazziEnableContextData(): Boolean {
  return getSystemProperty("roborazzi.contextdata", "true").toBoolean()
}

@Deprecated(
  message = "Use roborazziSystemPropertyTaskType()",
  replaceWith = ReplaceWith("roborazziSystemPropertyTaskType().isEnabled()"),
)
fun roborazziEnabled(): Boolean {
  return roborazziSystemPropertyTaskType().isEnabled()
}

@Deprecated(
  message = "Use roborazziSystemPropertyTaskType()",
  ReplaceWith("roborazziSystemPropertyTaskType().isRecord()")
)
fun roborazziRecordingEnabled(): Boolean {
  return roborazziSystemPropertyTaskType().isRecording()
}

@Deprecated(
  message = "Use roborazziSystemPropertyTaskType()",
  ReplaceWith("roborazziSystemPropertyTaskType().isComparing()")
)
fun roborazziCompareEnabled(): Boolean {
  return roborazziSystemPropertyTaskType().isComparing()
}

@Deprecated(
  message = "Use roborazziSystemPropertyTaskType()",
  ReplaceWith("roborazziSystemPropertyTaskType().isVerifying()")
)
fun roborazziVerifyEnabled(): Boolean {
  return roborazziSystemPropertyTaskType().isVerifying()
}

@ExperimentalRoborazziApi
fun roborazziSystemPropertyTaskType(): RoborazziTaskType {
  val result = run {
    val roborazziRecordingEnabled = getSystemProperty("roborazzi.test.record") == "true"
    val roborazziCompareEnabled = getSystemProperty("roborazzi.test.compare") == "true"
    val roborazziVerifyEnabled = getSystemProperty("roborazzi.test.verify") == "true"
    RoborazziTaskType.of(
      isRecording = roborazziRecordingEnabled,
      isComparing = roborazziCompareEnabled,
      isVerifying = roborazziVerifyEnabled
    )
  }
  debugLog {
    "roborazziSystemPropertyTaskType():\n" +
      "roborazziTaskType: $result \n" +
      "roborazziDefaultResizeScale(): ${roborazziDefaultResizeScale()}\n"
  }
  return result
}


/**
 * Specify the file path strategy for the recorded image.
 * Default: roborazzi.record.filePathStrategy=relativePathFromCurrentDirectory
 * If set to relativePathFromRoborazziContextOutputDirectory, the file will be saved to the output directory specified by RoborazziRule.Options.outputDirectoryPath.
 */
fun roborazziDefaultResizeScale(): Double {
  return checkNotNull(getSystemProperty("roborazzi.record.resizeScale", "1.0")).toDouble()
}

fun roborazziSystemPropertyProjectPath(): String {
  return getSystemProperty("roborazzi.project.path", ".")
}