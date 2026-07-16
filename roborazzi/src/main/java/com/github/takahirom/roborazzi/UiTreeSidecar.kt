package com.github.takahirom.roborazzi

import java.io.File

/**
 * Writes the `.uitree.json` sidecar next to the image that the current task
 * writes, when [RoborazziOptions.uiTreeDumpOptions] is enabled.
 *
 * The [serializationTree] lambda is only invoked when the feature is enabled, so
 * there is no traversal cost when it is off. The tree it returns should be built
 * with [UiTreeTraversalCaptureType] so the whole hierarchy is traversed without
 * fetching per-node bitmaps.
 *
 * Returns the [RoborazziOptions] to use for the actual image write: when the
 * sidecar was written, a copy whose `contextData` records the sidecar path under
 * [ROBORAZZI_UI_TREE_FILE_PATH_KEY]; otherwise the options unchanged.
 *
 * This is informational only and never throws in a way that would fail the
 * capture; any I/O problem is logged and swallowed.
 */
@OptIn(ExperimentalRoborazziApi::class)
@InternalRoborazziApi
fun writeUiTreeSidecarIfEnabled(
  serializationTree: () -> RoboComponentTree,
  goldenFile: File,
  roborazziOptions: RoborazziOptions,
): RoborazziOptions {
  val dumpOptions = roborazziOptions.uiTreeDumpOptions ?: return roborazziOptions
  return try {
    val tree = serializationTree()
    val scale = roborazziOptions.recordOptions.resizeScale
    val captureInfo = UiTreeCaptureInfo(
      imageWidth = (tree.width * scale).toInt(),
      imageHeight = (tree.height * scale).toInt(),
      scale = scale,
    )
    val json = tree.toUiTreeJson(captureInfo = captureInfo, options = dumpOptions)
    val sidecarFile = uiTreeSidecarFile(goldenFile, roborazziOptions)
    sidecarFile.parentFile?.mkdirs()
    sidecarFile.writeText(json)
    roborazziDebugLog { "UI tree sidecar written: ${sidecarFile.absolutePath}" }
    roborazziOptions.copy(
      contextData = roborazziOptions.contextData +
        (ROBORAZZI_UI_TREE_FILE_PATH_KEY to sidecarFile.absolutePath)
    )
  } catch (e: Exception) {
    // The sidecar is informational only; never fail the capture because of it.
    roborazziErrorLog("Roborazzi failed to write the UI tree sidecar: ${e.message}")
    roborazziOptions
  }
}

/**
 * Resolves the sidecar file path, mirroring where [processOutputImageAndReport]
 * writes the image for the current task: the golden file on record, and the
 * `_actual` image (in the compare output directory) on compare/verify. The
 * sidecar always describes the current run.
 */
@OptIn(ExperimentalRoborazziApi::class)
@InternalRoborazziApi
fun uiTreeSidecarFile(goldenFile: File, roborazziOptions: RoborazziOptions): File {
  val taskType = roborazziOptions.taskType
  val baseName = goldenFile.nameWithoutExtension
  val isPureCompareOrVerify =
    (taskType.isVerifying() || taskType.isComparing()) && !taskType.isRecording()
  return if (isPureCompareOrVerify) {
    File(
      roborazziOptions.compareOptions.outputDirectoryPath,
      baseName + "_actual" + roborazziUiTreeSidecarSuffix
    ).absoluteFile
  } else {
    File(goldenFile.parentFile, baseName + roborazziUiTreeSidecarSuffix).absoluteFile
  }
}
