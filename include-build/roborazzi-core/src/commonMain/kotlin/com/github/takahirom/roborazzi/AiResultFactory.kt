package com.github.takahirom.roborazzi

fun interface AiComparisonResultFactory {
  operator fun invoke(
    comparisonImageFilePath: String,
    aiCompareOptions: AiCompareOptions
  ): AiComparisonResult
}
