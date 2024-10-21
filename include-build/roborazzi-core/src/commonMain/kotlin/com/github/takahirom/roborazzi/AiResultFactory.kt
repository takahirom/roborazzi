package com.github.takahirom.roborazzi

fun interface AiCompareResultFactory {
  operator fun invoke(
    comparisonImageFilePath: String,
    aiOptions: AiOptions
  ): AiResult
}

var aiCompareResultFactory: AiCompareResultFactory? =
  AiCompareResultFactory { comparisonImageFilePath, aiOptions ->
    throw NotImplementedError("aiCompareCanvasFactory is not implemented")
  }
