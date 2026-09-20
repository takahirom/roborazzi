package com.github.takahirom.roborazzi.annotations

/** [RoboComposePreviewOptions.renderScale] value meaning "use the scale configured in Gradle". */
const val INHERIT_RENDER_SCALE: Double = -1.0

/**
 * Annotation for the @Preview composable function.
 * To use this annotation, you must include the roborazzi-compose-preview-scanner-support library.
 */
@Target(AnnotationTarget.FUNCTION)
annotation class RoboComposePreviewOptions(
  val manualClockOptions: Array<ManualClockOptions> = arrayOf(),
  /**
   * Rendering scale for this preview only, overriding the Gradle-level `renderScale`.
   * Defaults to [INHERIT_RENDER_SCALE], which keeps the configured value.
   *
   * Android previews only: `renderScale` is not supported for Compose Desktop previews.
   */
  val renderScale: Double = INHERIT_RENDER_SCALE,
) {

}


// TODO -> maybe add also parameter for ignoreFrames, as used in mainClock.advanceTime()
// TODO -> Docu: mention about the 16ms frame in Android
annotation class ManualClockOptions(
  val advanceTimeMillis: Long = 0L
)