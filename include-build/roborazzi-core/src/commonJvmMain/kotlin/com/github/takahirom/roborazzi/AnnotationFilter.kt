package com.github.takahirom.roborazzi

import java.io.Serializable

/**
 * Filter for composable previews by annotation.
 */
@ExperimentalRoborazziApi
sealed class AnnotationFilter : Serializable {
  /**
   * Exclude previews annotated with any of the given annotations.
   *
   * @param annotations Fully qualified annotation class names. For nested classes, use the
   * JVM binary name with `$`, e.g. `com.example.Outer$Inner`.
   */
  @ConsistentCopyVisibility
  data class Exclude private constructor(val annotations: List<String>) : AnnotationFilter() {
    constructor(vararg annotations: String) : this(annotations.toList())
  }

  companion object Filter {
    val RoboPreviewExclude = Exclude("com.github.takahirom.roborazzi.annotations.RoboPreviewExclude")
    val RoboPreviewInclude = Include("com.github.takahirom.roborazzi.annotations.RoboPreviewInclude")
  }

  /**
   * Include only previews annotated with any of the given annotations.
   *
   * @param annotations Fully qualified annotation class names. For nested classes, use the
   * JVM binary name with `$`, e.g. `com.example.Outer$Inner`.
   */
  @ConsistentCopyVisibility
  data class Include private constructor(val annotations: List<String>) : AnnotationFilter() {
    constructor(vararg annotations: String) : this(annotations.toList())
  }
}
