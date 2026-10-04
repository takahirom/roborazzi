package com.github.takahirom.roborazzi.annotations

/**
 * Marks a `@Preview` to be skipped during screenshot test generation.
 * Works out of the box: `annotationFilter` defaults to `AnnotationFilter.Filter.RoboPreviewExclude`.
 * Has no effect if `annotationFilter` is overridden with `AnnotationFilter.Include`.
 */
@Target(AnnotationTarget.FUNCTION)
annotation class RoboPreviewExclude
