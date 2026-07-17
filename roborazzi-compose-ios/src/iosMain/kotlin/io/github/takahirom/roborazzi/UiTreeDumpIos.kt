@file:OptIn(
  InternalRoborazziApi::class,
  ExperimentalRoborazziApi::class,
  ExperimentalForeignApi::class,
)

package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.ROBORAZZI_UI_TREE_FILE_PATH_KEY
import com.github.takahirom.roborazzi.RoboComponentTree
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.UiTreeCaptureInfo
import com.github.takahirom.roborazzi.assignUiTreeNumbers
import com.github.takahirom.roborazzi.roborazziAnnotatedImageSuffix
import com.github.takahirom.roborazzi.roborazziReportLog
import com.github.takahirom.roborazzi.roborazziUiTreeSidecarSuffix
import com.github.takahirom.roborazzi.toUiTreeJson
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.writeToFile

/**
 * Writes the `.uitree.json` sidecar next to the golden image on iOS, when
 * [RoborazziOptions.uiTreeDumpOptions] is enabled.
 *
 * Only the JSON sidecar is supported on iOS. The annotated Set-of-Mark PNG is
 * intentionally out of scope on iOS (it relies on the AWT drawing pipeline that
 * only exists on the JVM), so enabling `annotateImage` logs a notice and is
 * otherwise ignored rather than crashing.
 *
 * Naming and `_actual` basename semantics mirror the Android / Desktop
 * implementations. Informational only: any problem is logged and swallowed so it
 * never fails the capture. Returns the [RoborazziOptions] to use for the actual
 * image write: a copy whose `contextData` records the sidecar path when written.
 */
internal fun writeUiTreeDumpIfEnabledIos(
  serializationTree: () -> RoboComponentTree,
  resolvedGoldenFilePath: String,
  roborazziOptions: RoborazziOptions,
): RoborazziOptions {
  val dumpOptions = roborazziOptions.uiTreeDumpOptions ?: return roborazziOptions
  return try {
    if (dumpOptions.annotateImage) {
      roborazziReportLog(
        "Roborazzi: the annotated UI tree image is not supported on iOS; " +
          "writing the ${roborazziUiTreeSidecarSuffix} sidecar only " +
          "(annotated ${roborazziAnnotatedImageSuffix} is JVM-only)."
      )
    }
    val tree = serializationTree()
    val scale = roborazziOptions.recordOptions.resizeScale
    val captureInfo = UiTreeCaptureInfo(
      imageWidth = (tree.width * scale).toInt(),
      imageHeight = (tree.height * scale).toInt(),
      scale = scale,
    )
    val numbers = assignUiTreeNumbers(tree, dumpOptions.isAnnotatable)
    val json = tree.toUiTreeJson(captureInfo = captureInfo, numbers = numbers)

    val sidecarPath = uiTreeSidecarPathIos(resolvedGoldenFilePath, roborazziOptions)
    val parentPath = sidecarPath.substringBeforeLast("/")
    if (parentPath.isNotEmpty() && parentPath != sidecarPath) {
      NSFileManager.defaultManager.createDirectoryAtPath(
        parentPath,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
      )
    }
    val written = (json as NSString).writeToFile(
      path = sidecarPath,
      atomically = true,
      encoding = NSUTF8StringEncoding,
      error = null,
    )
    if (written) {
      roborazziReportLog("UI tree sidecar written: $sidecarPath")
      roborazziOptions.copy(
        contextData = roborazziOptions.contextData +
          (ROBORAZZI_UI_TREE_FILE_PATH_KEY to sidecarPath)
      )
    } else {
      roborazziReportLog("Roborazzi failed to write the UI tree sidecar to $sidecarPath")
      roborazziOptions
    }
  } catch (e: Exception) {
    // The sidecar is informational only; never fail the capture because of it.
    roborazziReportLog("Roborazzi failed to write the UI tree dump: ${e.message}")
    roborazziOptions
  }
}

/**
 * Resolves the sidecar path from the resolved golden image path, mirroring where
 * the shared pipeline writes the image for the current task: next to the golden
 * on record, and the `_actual` sidecar in the compare output directory on pure
 * compare/verify.
 */
private fun uiTreeSidecarPathIos(
  resolvedGoldenFilePath: String,
  roborazziOptions: RoborazziOptions,
): String {
  val fileName = resolvedGoldenFilePath.substringAfterLast("/")
  val baseName = fileName.substringBeforeLast(".")
  val isPureCompareOrVerify =
    (roborazziOptions.taskType.isVerifying() || roborazziOptions.taskType.isComparing()) &&
      !roborazziOptions.taskType.isRecording()
  return if (isPureCompareOrVerify) {
    val outputDir = roborazziOptions.compareOptions.outputDirectoryPath.trimEnd('/')
    "$outputDir/${baseName}_actual$roborazziUiTreeSidecarSuffix"
  } else {
    val parentPath = resolvedGoldenFilePath.substringBeforeLast("/")
    "$parentPath/$baseName$roborazziUiTreeSidecarSuffix"
  }
}
