package com.github.takahirom.roborazzi

import java.io.Serializable

/**
 * Filter for composable previews by annotation.
 */
@ExperimentalRoborazziApi
sealed class AnnotationFilter : Serializable {
  /**
   * Exclude only previews with the specified annotations, passing their absolute Path as String.
   * If the absolute path is a nested class, pass the JVM binary name using `$`, * e.g. `com.example.Outer$Inner`.
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
   * Include only previews with the specified annotations, passing their absolute Path as String.
   * If the absolute path is a nested class, pass the JVM binary name using `$`, * e.g. `com.example.Outer$Inner`.
   */
  @ConsistentCopyVisibility
  data class Include private constructor(val annotations: List<String>) : AnnotationFilter() {
    constructor(vararg annotations: String) : this(annotations.toList())
  }
}
