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
   * A value below 1.0 renders this preview at a lower density, which saves rendering time and
   * file size at the cost of fidelity. See the `renderScale` property of the Gradle extension
   * `generateComposePreviewRobolectricTests` for what the scale changes and what it does not.
   *
   * Android previews only: `renderScale` is not supported for Compose Desktop previews.
   */
  val renderScale: Double = INHERIT_RENDER_SCALE,
  /**
   * Records this preview as an animated image, in addition to its still screenshot.
   *
   * The video is recorded only when the Roborazzi task is recording; verify and compare keep
   * checking the still screenshot only. It cannot be combined with [manualClockOptions].
   * Every element produces one video named `<screenshot name>_VIDEO` (`_VIDEO_1`, `_VIDEO_2`, ...
   * when there are several).
   */
  val videoOptions: Array<PreviewVideoOptions> = arrayOf(),
) {

}


// TODO -> maybe add also parameter for ignoreFrames, as used in mainClock.advanceTime()
// TODO -> Docu: mention about the 16ms frame in Android
annotation class ManualClockOptions(
  val advanceTimeMillis: Long = 0L
)

/** Output format of a [PreviewVideoOptions] recording. */
enum class PreviewVideoFormat(val extension: String) {
  /** An animated GIF (256 colors). */
  GIF("gif"),

  /** A lossless, full-color animated PNG. */
  APNG("png"),
}

/**
 * Records a video of a preview, see [RoboComposePreviewOptions.videoOptions].
 *
 * The preview is composed with the clock paused, so its entrance animations are recorded from the
 * first frame. Only the preview itself is recorded, like its still screenshot.
 */
annotation class PreviewVideoOptions(
  /** Length of the recording in virtual time. The video ends exactly after this duration. */
  val durationMillis: Long = 1000L,
  /** Frames per second. GIF stores delays in 10ms steps, so prefer 10, 20, 25 or 50. */
  val fps: Int = 10,
  val format: PreviewVideoFormat = PreviewVideoFormat.GIF,
)
