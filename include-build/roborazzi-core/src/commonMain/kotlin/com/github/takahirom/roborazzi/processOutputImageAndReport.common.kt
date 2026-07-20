package com.github.takahirom.roborazzi

import com.dropbox.differ.ImageComparator
import kotlinx.io.files.Path

fun interface EmptyCanvasFactory {
  operator fun invoke(
    width: Int,
    height: Int,
    filled: Boolean,
    bufferedImageType: Int
  ): RoboCanvas
}

/**
 * Common (platform independent) counterpart of [CanvasFactoryFromFile] that
 * receives the golden file path as a string instead of a `java.io.File`.
 */
@InternalRoborazziApi
fun interface CanvasFactoryFromPath {
  operator fun invoke(
    filePath: String,
    bufferedImageType: Int
  ): RoboCanvas
}

fun interface ComparisonCanvasFactory {
  operator fun invoke(
    goldenRoboCanvas: RoboCanvas,
    newRoboImage: RoboCanvas,
    resizeScale: Double,
    bufferedImageType: Int
  ): RoboCanvas
}

/**
 * Golden file name suffixes reserved by Roborazzi for the generated `_compare`
 * and `_actual` images. A user-supplied golden file must not end with any of
 * these.
 */
internal val roborazziReservedGoldenFileSuffixes: List<String> = listOf("_compare", "_actual")

/**
 * Throws [IllegalArgumentException] when [goldenFilePath]'s name ends with a
 * suffix reserved by Roborazzi (see [roborazziReservedGoldenFileSuffixes]).
 */
internal fun validateGoldenFileNameOrThrow(goldenFilePath: String) {
  roborazziReservedGoldenFileSuffixes.forEach {
    if (goldenFilePath.nameWithoutExtension.endsWith(it)) {
      throw IllegalArgumentException("The file name should not end with $it because it is reserved for Roborazzi")
    }
  }
}

@InternalRoborazziApi
fun processOutputImageAndReport(
  newRoboCanvas: RoboCanvas,
  goldenFilePath: String,
  contextData: Map<String, Any>,
  roborazziOptions: RoborazziOptions,
  emptyCanvasFactory: EmptyCanvasFactory,
  canvasFactoryFromFile: CanvasFactoryFromPath,
  comparisonCanvasFactory: ComparisonCanvasFactory,
  // Reports whether this run (re)wrote the current-run output image (the golden on
  // record, the `_actual` image on a changed compare/verify). It is the explicit,
  // authoritative signal the annotated-image writer uses to decide whether to
  // annotate the freshly written image or fall back to the golden. It is invoked
  // BEFORE the (possibly throwing) verification report, so the annotation still
  // runs for a failing verify. Defaults to a no-op for callers that do not dump.
  reportActualImageWritten: (Boolean) -> Unit = {},
) {
  val taskType = roborazziOptions.taskType
  roborazziDebugLog {
    "processOutputImageAndReport(): " +
      "taskType:" + taskType +
      "\ngoldenFile:$goldenFilePath"
  }
  if (taskType.isEnabled() && !roborazziSystemPropertyTaskType().isEnabled()) {
    roborazziReportLog(
      "Roborazzi Warning:\n" +
        "You have specified '$taskType' without the necessary plugin configuration like roborazzi.test.record=true or ./gradlew recordRoborazziDebug.\n" +
        "This may complicate your screenshot testing process because the behavior is not changeable. And it doesn't allow Roborazzi plugin to generate test report.\n" +
        "Please ensure proper setup in gradle.properties or via Gradle tasks for optimal functionality."
    )
  }
  validateGoldenFileNameOrThrow(goldenFilePath)
  val recordOptions = roborazziOptions.recordOptions
  val resizeScale = recordOptions.resizeScale
  if (taskType.isVerifying() || taskType.isComparing()) {
    val width = (newRoboCanvas.croppedWidth * resizeScale).toInt()
    val height = (newRoboCanvas.croppedHeight * resizeScale).toInt()
    // Capture this before any write. For recording task types the actual image is
    // saved to the golden file, so checking existence later would always be true.
    val isGoldenFilePresent = roborazziFileExists(goldenFilePath)
    val goldenRoboCanvas = if (isGoldenFilePresent) {
      canvasFactoryFromFile(goldenFilePath, recordOptions.pixelBitConfig.toBufferedImageType())
    } else {
      emptyCanvasFactory(
        width = width,
        height = height,
        filled = true,
        bufferedImageType = recordOptions.pixelBitConfig.toBufferedImageType()
      )
    }

    try {
      // Only used by CaptureResult.Changed
      var diffPercentage: Float? = null

      val compareOptions = roborazziOptions.compareOptions
      val changed = if (height == goldenRoboCanvas.height && width == goldenRoboCanvas.width) {
        val comparisonResult: ImageComparator.ComparisonResult =
          newRoboCanvas.differ(
            other = goldenRoboCanvas,
            resizeScale = resizeScale,
            imageComparator = compareOptions.imageComparator
          )
        diffPercentage = comparisonResult.pixelDifferences.toFloat() / comparisonResult.pixelCount
        val changed = !compareOptions.resultValidator(comparisonResult)
        roborazziReportLog("${goldenFilePath.name} The differ result :$comparisonResult changed:$changed")
        changed
      } else {
        roborazziReportLog("${goldenFilePath.name} The image size is changed. actual = (${goldenRoboCanvas.width}, ${goldenRoboCanvas.height}), golden = (${newRoboCanvas.croppedWidth}, ${newRoboCanvas.croppedHeight})")
        true
      }

      val result: CaptureResult = if (changed) {
        val comparisonFilePath = resolveComparisonImagePath(
          compareOutputDirectoryPath = compareOptions.outputDirectoryPath,
          goldenFilePath = goldenFilePath,
          suffix = "_compare",
        )
        val comparisonCanvas = comparisonCanvasFactory(
          goldenRoboCanvas = goldenRoboCanvas,
          newRoboImage = newRoboCanvas,
          resizeScale = resizeScale,
          bufferedImageType = recordOptions.pixelBitConfig.toBufferedImageType()
        )
        comparisonCanvas
          .save(
            path = comparisonFilePath,
            resizeScale = resizeScale,
            contextData = contextData,
            imageIoFormat = recordOptions.imageIoFormat,
          )
        roborazziDebugLog {
          "processOutputImageAndReport(): compareCanvas is saved " +
            "compareFile:$comparisonFilePath"
        }
        comparisonCanvas.release()

        val actualFilePath = if (taskType.isRecording()) {
          // If record option is enabled, we should save the actual file as the golden file.
          goldenFilePath
        } else {
          resolveComparisonImagePath(
            compareOutputDirectoryPath = compareOptions.outputDirectoryPath,
            goldenFilePath = goldenFilePath,
            suffix = "_actual",
          )
        }
        newRoboCanvas
          .save(
            path = actualFilePath,
            resizeScale = resizeScale,
            contextData = contextData,
            imageIoFormat = recordOptions.imageIoFormat,
          )
        // The current-run output image was written (the `_actual`, or the golden
        // for a recording task type). Signal it before the report, which throws
        // on a failing verify.
        reportActualImageWritten(true)
        val aiOptions = compareOptions.aiAssertionOptions
        val aiResult = if (aiOptions != null && aiOptions.aiAssertions.isNotEmpty()) {
          aiOptions.aiAssertionModel.assert(
            referenceImageFilePath = goldenFilePath,
            comparisonImageFilePath = comparisonFilePath,
            actualImageFilePath = actualFilePath,
            aiAssertionOptions = aiOptions
          )
        } else {
          null
        }
        roborazziDebugLog {
          "processOutputImageAndReport(): actualCanvas is saved " +
            "actualFile:$actualFilePath"
        }
        if (isGoldenFilePresent) {
          CaptureResult.Changed(
            compareFile = comparisonFilePath,
            actualFile = actualFilePath,
            goldenFile = goldenFilePath,
            timestampNs = roborazziCurrentTimeNs(),
            diffPercentage = diffPercentage,
            aiAssertionResults = aiResult,
            contextData = contextData,
          )
        } else {
          CaptureResult.Added(
            compareFile = comparisonFilePath,
            actualFile = actualFilePath,
            goldenFile = goldenFilePath,
            timestampNs = roborazziCurrentTimeNs(),
            aiAssertionResults = aiResult,
            contextData = contextData,
          )
        }
      } else {
        // Nothing new was written this run; a pre-existing `_actual` from an
        // earlier changed run must NOT be treated as the current output.
        reportActualImageWritten(false)
        CaptureResult.Unchanged(
          goldenFile = goldenFilePath,
          timestampNs = roborazziCurrentTimeNs(),
          contextData = contextData,
        )
      }
      roborazziDebugLog {
        "processOutputImageAndReport: \n" +
          "  goldenFile: $goldenFilePath\n" +
          "  changed: $changed\n" +
          "  result: $result\n"
      }
      roborazziOptions.reportOptions.captureResultReporter.report(
        captureResult = result,
        roborazziTaskType = taskType
      )
    } finally {
      // The golden canvas holds a native image resource on platforms
      // without garbage collection (e.g. iOS CoreGraphics contexts), so it
      // must be released explicitly once comparison/reporting is done.
      goldenRoboCanvas.release()
    }
  } else {
    // roborazzi.record is checked before
    newRoboCanvas.save(
      path = goldenFilePath,
      resizeScale = resizeScale,
      contextData = contextData,
      imageIoFormat = recordOptions.imageIoFormat,
    )
    // The recording task type writes the golden, which is also the annotated
    // image's source.
    reportActualImageWritten(true)
    roborazziDebugLog {
      "processOutputImageAndReport: \n" +
        " record goldenFile: $goldenFilePath\n"
    }
    roborazziOptions.reportOptions.captureResultReporter.report(
      captureResult = CaptureResult.Recorded(
        goldenFile = goldenFilePath,
        timestampNs = roborazziCurrentTimeNs(),
        contextData = contextData,
      ),
      roborazziTaskType = taskType
    )
  }
}

private fun resolveOutputPath(directoryPath: String, fileName: String): String {
  return roborazziToAbsolutePath(Path(directoryPath, fileName).toString())
}

/**
 * Resolves the path of a generated `_compare` / `_actual` image so that it mirrors
 * the golden's subdirectory structure under [compareOutputDirectoryPath].
 *
 * Historically these paths were built only from the golden's leaf file name
 * ([nameWithoutExtension]). That collides under the subdirectory naming strategies
 * ([DefaultFileNameGenerator.DefaultNamingStrategy.TestPackageDirAndClassAndMethod]
 * and [DefaultFileNameGenerator.DefaultNamingStrategy.TestNestedPackageDirAndClassAndMethod]):
 * two goldens with the same simple class + method name in different packages, e.g.
 * `com/a/FooTest.test.png` and `com/b/FooTest.test.png`, share the same leaf name
 * and would overwrite each other's single flat `FooTest.test_compare.png`.
 *
 * The golden's subdirectory (relative to the directory that contains it) is
 * preserved so the comparison image lands next to its golden. For the flat naming
 * strategies the golden sits directly in the output directory, so the resolved
 * subdirectory is empty and the result is byte-for-byte identical to the previous
 * leaf-name behavior.
 */
private fun resolveComparisonImagePath(
  compareOutputDirectoryPath: String,
  goldenFilePath: String,
  suffix: String,
): String {
  val comparisonFileName =
    goldenFilePath.nameWithoutExtension + suffix + "." + goldenFilePath.extension
  val subdirectory = goldenSubdirectoryUnder(
    baseDirectoryPath = compareOutputDirectoryPath,
    goldenFilePath = goldenFilePath,
  )
  val relativePath = if (subdirectory.isEmpty()) {
    comparisonFileName
  } else {
    Path(subdirectory, comparisonFileName).toString()
  }
  return resolveOutputPath(compareOutputDirectoryPath, relativePath)
}

/**
 * Returns the golden's package subdirectory to reproduce under the compare output
 * directory, or an empty string to fall back to the historical leaf-name placement.
 *
 * Resolution order:
 * 1. Relative to the compare output directory ([baseDirectoryPath]). In the default
 *    and shared-directory layouts the compare output directory and the golden output
 *    directory are the same directory, so this yields exactly the package
 *    subdirectory produced by the naming strategy. For the three flat naming
 *    strategies the golden sits directly there, so this is empty (leaf-name,
 *    byte-for-byte unchanged).
 * 2. Only when the compare output directory is a SEPARATE directory that does not
 *    contain the golden AND the active strategy is one of the directory-encoding
 *    `*Dir` strategies, relative to the Roborazzi output directory. A path alone
 *    cannot distinguish a package subdirectory (naming strategy) from a
 *    user-supplied custom `filePath` subdirectory, so the explicitly configured
 *    `*Dir` strategy is the signal that the subdirectories are package structure and
 *    must be mirrored. For every other strategy this second step is skipped, so a
 *    custom `filePath` keeps the historical leaf-name placement (see
 *    compareWithCustomPath).
 */
@OptIn(ExperimentalRoborazziApi::class)
private fun goldenSubdirectoryUnder(
  baseDirectoryPath: String,
  goldenFilePath: String,
): String {
  val goldenParent = Path(roborazziToAbsolutePath(goldenFilePath)).parent ?: return ""
  val underCompareDir = relativeSubdirectoryOrNull(goldenParent, baseDirectoryPath)
  if (underCompareDir != null) return underCompareDir
  // The golden is not located under the compare output directory. Mirror its
  // package subtree only for the directory-encoding naming strategies.
  if (roborazziIsSubdirectoryNamingStrategy()) {
    val underOutputDir =
      relativeSubdirectoryOrNull(goldenParent, roborazziSystemPropertyOutputDirectory())
    if (underOutputDir != null) return underOutputDir
  }
  return ""
}

/**
 * The path of [goldenParent] relative to [baseDirectoryPath], or null when the
 * golden sits directly in that directory (no subdirectory) or is not located under
 * it at all (a relative path escaping it with "..").
 */
private fun relativeSubdirectoryOrNull(goldenParent: Path, baseDirectoryPath: String): String? {
  val relative = goldenParent.relativeTo(Path(roborazziToAbsolutePath(baseDirectoryPath))).toString()
  return if (relative.isEmpty() || relative.startsWith("..")) null else relative
}
