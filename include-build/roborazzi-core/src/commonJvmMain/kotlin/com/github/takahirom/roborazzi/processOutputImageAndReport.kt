package com.github.takahirom.roborazzi

import java.io.File

fun interface CanvasFactoryFromFile {
  operator fun invoke(
    file: File,
    bufferedImageType: Int
  ): RoboCanvas
}

@InternalRoborazziApi
fun processOutputImageAndReport(
  newRoboCanvas: RoboCanvas,
  goldenFile: File,
  roborazziOptions: RoborazziOptions,
  emptyCanvasFactory: EmptyCanvasFactory,
  canvasFactoryFromFile: CanvasFactoryFromFile,
  comparisonCanvasFactory: ComparisonCanvasFactory,
  // See the common overload: reports whether this run (re)wrote the current-run
  // output image, so the annotated-image writer can pick the correct source.
  reportActualImageWritten: (Boolean) -> Unit = {},
) {
  // Validate the golden file name BEFORE building the context data so the
  // reserved-suffix check happens first, matching the observable ordering of the
  // former monolithic implementation. The common impl re-checks afterwards,
  // which is harmless.
  validateGoldenFileNameOrThrow(goldenFile.absolutePath)
  processOutputImageAndReport(
    newRoboCanvas = newRoboCanvas,
    goldenFilePath = goldenFile.absolutePath,
    contextData = buildContextData(roborazziOptions),
    roborazziOptions = roborazziOptions,
    emptyCanvasFactory = emptyCanvasFactory,
    canvasFactoryFromFile = { filePath, bufferedImageType ->
      canvasFactoryFromFile(File(filePath), bufferedImageType)
    },
    comparisonCanvasFactory = comparisonCanvasFactory,
    reportActualImageWritten = reportActualImageWritten,
  )
}

@InternalRoborazziApi
private fun buildContextData(roborazziOptions: RoborazziOptions): Map<String, Any> {
  val base = applyContextDataPolicy(roborazziOptions)
  if (!roborazziEnableContextData()) return base
  // JVM-only: inject the test class name derived from provideRoborazziContext().
  val className = provideRoborazziContext().description?.className
  val classNameMap: Map<out String, Any> = className?.let {
    mapOf(
      RoborazziReportConst.DefaultContextData.DescriptionClass.key to className.toString()
    )
  } ?: mapOf()
  return base + classNameMap
}
