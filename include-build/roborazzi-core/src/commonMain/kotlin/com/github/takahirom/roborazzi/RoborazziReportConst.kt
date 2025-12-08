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

  fun getResultsSummaryFilePathFromBuildDir(variantName: String) =
    "test-results/roborazzi/$variantName/results-summary.json"
  fun getResultDirPathFromBuildDir(variantName: String) =
    "test-results/roborazzi/$variantName"
  fun getReportFilePathFromBuildDir(variantName: String) =
    "reports/roborazzi/$variantName/index.html"

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