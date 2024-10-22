package com.github.takahirom.roborazzi

fun interface AiComparisonResultFactory {
  operator fun invoke(
    comparisonImageFilePath: String,
    aiCompareOptions: AiCompareOptions
  ): AiComparisonResult
}

var aiComparisonResultFactory: AiComparisonResultFactory? =
  AiComparisonResultFactory { comparisonImageFilePath, aiOptions ->
    throw NotImplementedError("aiCompareCanvasFactory is not implemented")
  }
