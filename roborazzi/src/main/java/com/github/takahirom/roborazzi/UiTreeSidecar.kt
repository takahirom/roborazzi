package com.github.takahirom.roborazzi

import java.io.File

/**
 * The outcome of preparing the UI tree dump for a capture.
 *
 * [effectiveOptions] is the [RoborazziOptions] to use for the actual image write:
 * when the dump was written, a copy whose `contextData` records the sidecar (and,
 * when enabled, the annotated image) path; otherwise the options unchanged.
 *
 * [writeAnnotatedImage] must be invoked AFTER the output image has been written
 * to disk (it copies that image and draws the numbered boxes on top). It is a
 * no-op when the feature is disabled or annotation is opted out, and it never
 * throws in a way that would fail the capture.
 */
@InternalRoborazziApi
class UiTreeDumpWriteResult internal constructor(
  val effectiveOptions: RoborazziOptions,
  private val annotatedImageWriter: (() -> Unit)?,
) {
  fun writeAnnotatedImage() {
    annotatedImageWriter?.invoke()
  }
}

/**
 * Writes the `.uitree.json` sidecar next to the image that the current task
 * writes, when [RoborazziOptions.uiTreeDumpOptions] is enabled, and prepares the
 * annotated Set-of-Mark image (drawn later via
 * [UiTreeDumpWriteResult.writeAnnotatedImage]).
 *
 * The [serializationTree] lambda is only invoked when the feature is enabled, so
 * there is no traversal cost when it is off. The tree it returns should be built
 * with [UiTreeTraversalCaptureType] so the whole hierarchy is traversed without
 * fetching per-node bitmaps.
 *
 * The node numbering is computed once here and shared between the JSON sidecar
 * and the annotated image, so the two always agree.
 *
 * This is informational only and never throws in a way that would fail the
 * capture; any I/O problem is logged and swallowed.
 */
@OptIn(ExperimentalRoborazziApi::class)
@InternalRoborazziApi
fun writeUiTreeDumpIfEnabled(
  serializationTree: () -> RoboComponentTree,
  goldenFile: File,
  roborazziOptions: RoborazziOptions,
): UiTreeDumpWriteResult {
  val dumpOptions = roborazziOptions.uiTreeDumpOptions
    ?: return UiTreeDumpWriteResult(roborazziOptions, annotatedImageWriter = null)
  return try {
    val tree = serializationTree()
    val scale = roborazziOptions.recordOptions.resizeScale
    val captureInfo = UiTreeCaptureInfo(
      imageWidth = (tree.width * scale).toInt(),
      imageHeight = (tree.height * scale).toInt(),
      scale = scale,
    )
    // Compute the numbering once so the JSON sidecar and annotated image agree.
    val numbers = assignUiTreeNumbers(tree, dumpOptions.isAnnotatable)
    val json = tree.toUiTreeJson(captureInfo = captureInfo, numbers = numbers)
    val sidecarFile = uiTreeSidecarFile(goldenFile, roborazziOptions)
    sidecarFile.parentFile?.mkdirs()
    sidecarFile.writeText(json)
    roborazziDebugLog { "UI tree sidecar written: ${sidecarFile.absolutePath}" }

    var contextData = roborazziOptions.contextData +
      (ROBORAZZI_UI_TREE_FILE_PATH_KEY to sidecarFile.absolutePath)

    val annotatedImageWriter: (() -> Unit)? = if (dumpOptions.annotateImage) {
      val annotatedFile = annotatedImageFile(goldenFile, roborazziOptions)
      contextData = contextData + (ROBORAZZI_ANNOTATED_FILE_PATH_KEY to annotatedFile.absolutePath)
      val annotations = computeUiTreeAnnotations(tree, numbers, captureInfo)
      val sourceImageFile = currentRunImageFile(goldenFile, roborazziOptions)
      // Snapshot the source image state BEFORE the current task writes the output
      // image, so the annotated writer (invoked afterwards) can tell whether the
      // `_actual` image was actually (re)written this run. On an unchanged verify
      // no `_actual` is written, so a stale one left by an earlier run must not be
      // picked up; the writer then falls back to the identical golden.
      val sourceExistedBefore = sourceImageFile.exists()
      val sourceLastModifiedBefore = if (sourceExistedBefore) sourceImageFile.lastModified() else 0L
      val writer: () -> Unit = {
        val sourceWrittenThisRun = sourceImageFile.exists() &&
          (!sourceExistedBefore || sourceImageFile.lastModified() != sourceLastModifiedBefore)
        writeAnnotatedImage(
          sourceImageFile = sourceImageFile,
          sourceWrittenThisRun = sourceWrittenThisRun,
          fallbackImageFile = goldenFile,
          annotatedFile = annotatedFile,
          annotations = annotations,
          roborazziOptions = roborazziOptions,
        )
      }
      writer
    } else {
      null
    }

    UiTreeDumpWriteResult(
      effectiveOptions = roborazziOptions.copy(contextData = contextData),
      annotatedImageWriter = annotatedImageWriter,
    )
  } catch (e: Exception) {
    // The dump is informational only; never fail the capture because of it.
    roborazziErrorLog("Roborazzi failed to write the UI tree dump: ${e.message}")
    UiTreeDumpWriteResult(roborazziOptions, annotatedImageWriter = null)
  }
}

/**
 * True when the current task only compares/verifies (and does not record), so the
 * current-run output goes to the `_actual` image in the compare output directory
 * rather than to the golden file.
 */
@OptIn(ExperimentalRoborazziApi::class)
private fun RoborazziOptions.isPureCompareOrVerify(): Boolean =
  (taskType.isVerifying() || taskType.isComparing()) && !taskType.isRecording()

/**
 * Resolves the sidecar file path, mirroring where [processOutputImageAndReport]
 * writes the image for the current task: the golden file on record, and the
 * `_actual` image (in the compare output directory) on compare/verify. The
 * sidecar always describes the current run.
 */
@OptIn(ExperimentalRoborazziApi::class)
@InternalRoborazziApi
fun uiTreeSidecarFile(goldenFile: File, roborazziOptions: RoborazziOptions): File {
  val baseName = goldenFile.nameWithoutExtension
  return if (roborazziOptions.isPureCompareOrVerify()) {
    File(
      roborazziOptions.compareOptions.outputDirectoryPath,
      baseName + "_actual" + roborazziUiTreeSidecarSuffix
    ).absoluteFile
  } else {
    File(goldenFile.parentFile, baseName + roborazziUiTreeSidecarSuffix).absoluteFile
  }
}

/**
 * Resolves the annotated Set-of-Mark image path, mirroring [uiTreeSidecarFile]:
 * `MyTest.annotated.png` on record, `MyTest_actual.annotated.png` on pure
 * compare/verify.
 */
@OptIn(ExperimentalRoborazziApi::class)
@InternalRoborazziApi
fun annotatedImageFile(goldenFile: File, roborazziOptions: RoborazziOptions): File {
  val baseName = goldenFile.nameWithoutExtension
  return if (roborazziOptions.isPureCompareOrVerify()) {
    File(
      roborazziOptions.compareOptions.outputDirectoryPath,
      baseName + "_actual" + roborazziAnnotatedImageSuffix
    ).absoluteFile
  } else {
    File(goldenFile.parentFile, baseName + roborazziAnnotatedImageSuffix).absoluteFile
  }
}

/**
 * The image file the current task writes and that the annotated image copies:
 * the golden file on record, and the `_actual` image on pure compare/verify. On
 * an unchanged verify no `_actual` image is written, so the caller falls back to
 * the (identical) golden image.
 */
@OptIn(ExperimentalRoborazziApi::class)
private fun currentRunImageFile(goldenFile: File, roborazziOptions: RoborazziOptions): File {
  return if (roborazziOptions.isPureCompareOrVerify()) {
    File(
      roborazziOptions.compareOptions.outputDirectoryPath,
      goldenFile.nameWithoutExtension + "_actual." + goldenFile.extension
    ).absoluteFile
  } else {
    goldenFile
  }
}
