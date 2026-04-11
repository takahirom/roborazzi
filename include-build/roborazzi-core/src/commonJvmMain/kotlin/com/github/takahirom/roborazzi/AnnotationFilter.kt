package com.github.takahirom.roborazzi

import java.io.Serializable

/**
 * Filter for composable previews by annotation.
 */
@ExperimentalRoborazziApi
sealed class AnnotationFilter : Serializable {
  /**
   * Exclude previews with the specified annotations.
   */
  @ConsistentCopyVisibility
  data class Exclude private constructor(val annotations: List<String>) : AnnotationFilter() {
    constructor(vararg annotations: String) : this(annotations.toList())
  }

  /**
   * Include only previews with the specified annotations.
   */
  @ConsistentCopyVisibility
  data class Include private constructor(val annotations: List<String>) : AnnotationFilter() {
    constructor(vararg annotations: String) : this(annotations.toList())
  }
}
