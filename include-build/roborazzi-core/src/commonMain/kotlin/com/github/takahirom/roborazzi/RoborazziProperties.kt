package com.github.takahirom.roborazzi

const val DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH: String = "build/outputs/roborazzi"
var ROBORAZZI_DEBUG: Boolean = getSystemProperty("roborazzi.debug")?.toBoolean() ?: false

@ExperimentalRoborazziApi
fun roborazziSystemPropertyOutputDirectory(): String {
  return getSystemProperty("roborazzi.output.dir", DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH)
}

@ExperimentalRoborazziApi
fun roborazziSystemPropertyCompareOutputDirectory(): String {
  return getSystemProperty("roborazzi.compare.output.dir", DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH)
}

@ExperimentalRoborazziApi
fun roborazziSystemPropertyImageExtension(): String {
  return getSystemProperty("roborazzi.record.image.extension", "png")
}

@ExperimentalRoborazziApi
fun roborazziSystemPropertyResultDirectory(): String {
  // The fallback path is for backward compatibility with non-Gradle environments (e.g., Bazel).
  // When using the Gradle plugin, this property is set to a variant-aware path.
  return getSystemProperty("roborazzi.result.dir")
    ?: "build/test-results/roborazzi/results/"
}

@ExperimentalRoborazziApi
// This will be removed when we found if this is safe.
fun roborazziEnableContextData(): Boolean {
  return getSystemProperty("roborazzi.contextdata", "true").toBoolean()
}

/**
 * Applies the [roborazziEnableContextData] flag to the user-supplied context data.
 * When the flag is disabled the context data is dropped (empty map); when enabled
 * the user-supplied [RoborazziOptions.contextData] is returned as-is.
 *
 * Platform facades (e.g. iOS) use this directly. The JVM facade additionally
 * injects default context data (the test class name) on top of the result; that
 * default is JVM-only and is not part of this common policy.
 */
@InternalRoborazziApi
fun applyContextDataPolicy(roborazziOptions: RoborazziOptions): Map<String, Any> =
  if (roborazziEnableContextData()) {
    roborazziOptions.contextData
  } else {
    // This will be removed when we found if this is safe.
    emptyMap()
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
  roborazziDebugLog {
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