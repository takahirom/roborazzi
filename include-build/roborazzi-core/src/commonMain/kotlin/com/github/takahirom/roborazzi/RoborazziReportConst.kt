package com.github.takahirom.roborazzi

@InternalRoborazziApi
object RoborazziReportConst {
  @Deprecated(
    message = "Use resultsSummaryFilePathFromBuildDir instead",
    replaceWith = ReplaceWith("resultsSummaryFilePathFromBuildDir"),
    level = DeprecationLevel.ERROR
  )
  const val resultsSummaryFilePath = "build/test-results/roborazzi/results-summary.json"
  @Deprecated(
    message = "Use resultDirPathFromBuildDir instead",
    replaceWith = ReplaceWith("resultDirPathFromBuildDir"),
    level = DeprecationLevel.ERROR
  )
  const val resultDirPath = "build/test-results/roborazzi/results/"
  @Deprecated(
    message = "Use reportFilePathFromBuildDir instead",
    replaceWith = ReplaceWith("reportFilePathFromBuildDir"),
    level = DeprecationLevel.ERROR
  )
  const val reportFilePath = "build/reports/roborazzi/index.html"

  const val resultsSummaryFilePathFromBuildDir = "test-results/roborazzi/results-summary.json"
  const val resultDirPathFromBuildDir = "test-results/roborazzi/results/"
  const val reportFilePathFromBuildDir = "reports/roborazzi/index.html"

  sealed interface DefaultContextData {
    val key: String
    val title: String

    object DescriptionClass : DefaultContextData {
      override val key = "roborazzi_description_class"
      override val title = "Class"
    }

    companion object {
      fun keyToTitle(key: String): String {
        return when (key) {
          DescriptionClass.key -> DescriptionClass.title
          else -> key
        }
      }
    }
  }
}