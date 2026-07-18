package com.github.takahirom.roborazzi.annotations

/**
 * Marks a `@Preview` to be captured when `annotationFilter` is switched to opt-in mode,
 * e.g. `annotationFilter = AnnotationFilter.Filter.RoboPreviewInclude`.
 * Has no effect under the default `AnnotationFilter.Filter.RoboPreviewExclude` policy.
 */
@Target(AnnotationTarget.FUNCTION)
annotation class RoboPreviewInclude
