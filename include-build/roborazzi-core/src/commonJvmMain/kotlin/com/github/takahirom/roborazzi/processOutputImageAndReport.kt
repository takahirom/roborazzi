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
