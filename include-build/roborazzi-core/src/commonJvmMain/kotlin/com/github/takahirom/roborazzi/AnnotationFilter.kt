package com.github.takahirom.roborazzi

import java.io.Serializable

/**
 * Filter for composable previews by annotation.
 */
@ExperimentalRoborazziApi
sealed class AnnotationFilter : Serializable {
  /**
   * Exclude only previews with the specified annotations.
   */
  @ConsistentCopyVisibility
  data class Exclude private constructor(val annotations: List<String>) : AnnotationFilter() {
    constructor(vararg annotations: String) : this(annotations.toList())
  }

  companion object Filter {
    val ExcludeRoborazzi = Exclude("com.github.takahirom.roborazzi.annotations.filter.ExcludeFromRoborazzi")
    val IncludeRoborazzi = Include("com.github.takahirom.roborazzi.annotations.filter.IncludeInRoborazzi")
  }

  /**
   * Include only previews with the specified annotations.
   */
  @ConsistentCopyVisibility
  data class Include private constructor(val annotations: List<String>) : AnnotationFilter() {
    constructor(vararg annotations: String) : this(annotations.toList())
  }
}
