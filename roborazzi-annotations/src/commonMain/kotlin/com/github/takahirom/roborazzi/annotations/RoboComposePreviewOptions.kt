package com.github.takahirom.roborazzi.annotations

/**
 * Annotation for the @Preview composable function.
 * To use this annotation, you must include the roborazzi-compose-preview-scanner-support library.
 */
@Target(AnnotationTarget.FUNCTION)
annotation class RoboComposePreviewOptions(
  val manualClockOptions: Array<ManualClockOptions> = arrayOf()
) {

}


// TODO -> maybe add also parameter for ignoreFrames, as used in mainClock.advanceTime()
// TODO -> Docu: mention about the 16ms frame in Android
annotation class ManualClockOptions(
  val advanceTimeMillis: Long = 0L
)