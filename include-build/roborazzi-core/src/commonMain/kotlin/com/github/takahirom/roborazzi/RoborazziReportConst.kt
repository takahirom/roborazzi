package com.github.takahirom.roborazzi

@InternalRoborazziApi
object RoborazziReportConst {
  fun getResultsSummaryFilePathFromBuildDir(variantName: String) =
    "test-results/roborazzi/$variantName/results-summary.json"
  fun getResultDirPathFromBuildDir(variantName: String) =
    "test-results/roborazzi/$variantName/results/"
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