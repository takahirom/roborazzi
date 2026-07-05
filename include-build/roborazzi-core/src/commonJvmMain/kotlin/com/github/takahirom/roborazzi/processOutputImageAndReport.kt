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
  )
}

@InternalRoborazziApi
private fun buildContextData(roborazziOptions: RoborazziOptions): Map<String, Any> =
  if (roborazziEnableContextData()) {
    val className = provideRoborazziContext().description?.className
    val classNameMap: Map<out String, Any> = className?.let {
      mapOf(
        RoborazziReportConst.DefaultContextData.DescriptionClass.key to className.toString()
      )
    } ?: mapOf()
    roborazziOptions.contextData + classNameMap
  } else {
    // This will be removed when we found if this is safe.
    mapOf()
  }
