package com.github.takahirom.roborazzi.annotations

/**
 * Annotation for excluding a @Preview composable function from Roborazzi's preview screenshot tests.
 *
 * When a @Preview function is annotated with @RoboComposePreviewIgnore, it will be excluded
 * from the generated preview screenshot tests.
 *
 * Example:
 * ```
 * @RoboComposePreviewIgnore
 * @Preview
 * @Composable
 * fun MyPreview() {
 *   // This preview will be excluded from screenshot tests
 * }
 * ```
 *
 * To use this annotation, you must include the roborazzi-compose-preview-scanner-support library.
 *
 * Note: If you use a custom tester class that overrides the scanner configuration without calling
 * `excludeIfAnnotatedWithAnyOf(RoboComposePreviewIgnore::class.java)`, this annotation will have no effect.
 */
@Target(AnnotationTarget.FUNCTION)
annotation class RoboComposePreviewIgnore
