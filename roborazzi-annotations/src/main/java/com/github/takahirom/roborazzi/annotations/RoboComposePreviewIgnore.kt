package com.github.takahirom.roborazzi.annotations

/**
 * Annotation for excluding a @Preview composable function from Roborazzi's Compose Preview Support.
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
 * Note: If you use a custom tester class that overrides the scanner configuration without calling
 * `excludeIfAnnotatedWithAnyOf(RoboComposePreviewIgnore::class.java)`, this annotation will have no effect.
 */
@Target(AnnotationTarget.FUNCTION)
annotation class RoboComposePreviewIgnore
