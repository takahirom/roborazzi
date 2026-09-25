package com.github.takahirom.roborazzi

/**
 * Wraps each capture a [DesktopComposePreviewTester] performs for a shard.
 *
 * With scene reuse on, several previews are captured inside one `runDesktopComposeUiTest` call, so
 * a test runner can no longer tell where one preview ends and the next begins by watching method
 * invocations. Routing every capture through this listener keeps that boundary visible: the runner
 * reports each preview as its own test, and a preview that fails its comparison does not take the
 * rest of the scene down with it.
 */
@ExperimentalRoborazziApi
fun interface DesktopPreviewCaptureListener {
  /**
   * Runs [capture] for [testParameter].
   *
   * An implementation that reports results is expected to swallow what [capture] throws, record it
   * against [testParameter] and return normally, so the remaining previews in the same scene still
   * run.
   */
  fun aroundCapture(testParameter: DesktopPreviewTestParameter, capture: () -> Unit)

  companion object {
    /** Runs the capture and lets it throw: the behaviour of a plain, unreported call. */
    val None: DesktopPreviewCaptureListener =
      DesktopPreviewCaptureListener { _, capture -> capture() }
  }
}
