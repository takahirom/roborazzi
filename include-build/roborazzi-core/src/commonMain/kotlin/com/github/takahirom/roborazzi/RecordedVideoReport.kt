package com.github.takahirom.roborazzi

/**
 * Reports a saved video as a recorded result, so the Gradle plugin knows the file belongs to this
 * run (its `cleanupOldScreenshots` deletes managed images that no result refers to) and the report
 * lists it. Does nothing unless the task is recording.
 *
 * [absoluteFilePath] must be the absolute path of the saved video.
 */
@InternalRoborazziApi
fun reportRecordedVideo(absoluteFilePath: String, roborazziOptions: RoborazziOptions) {
  val taskType = roborazziOptions.taskType
  if (!taskType.isRecording()) return
  roborazziOptions.reportOptions.captureResultReporter.report(
    captureResult = CaptureResult.Recorded(
      goldenFile = absoluteFilePath,
      timestampNs = roborazziCurrentTimeNs(),
      contextData = emptyMap(),
    ),
    roborazziTaskType = taskType,
  )
}
