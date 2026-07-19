package com.github.takahirom.roborazzi

import java.io.File

/**
 * Bridge for publishing captured image files as test metadata.
 *
 * When a custom TestEngine (e.g. RoborazziVintageTestEngine from the
 * `roborazzi-junit-platform-reporting` module) is on the classpath, it sets a
 * publisher via [setPublisher] before each test. Calls to [publishFile] then
 * forward images to JUnit Platform's `EngineExecutionListener.fileEntryPublished()`,
 * which Gradle 9.4+ renders as test attachments.
 *
 * When no engine wrapper is present, no publisher is set and [publishFile] is a no-op.
 *
 * This type intentionally has no JUnit Platform dependency: it only exchanges a
 * `(File) -> Unit` callback, so `roborazzi-core` stays free of any JUnit Platform
 * types.
 *
 * **Threading**: The publisher is stored in a [ThreadLocal]. It must be set and read on
 * the same thread. The Vintage engine calls [setPublisher] via `executionStarted` and runs
 * the test body on the same thread, so this works in standard Robolectric test execution.
 * If `captureRoboImage` is called from a different thread (e.g. a background coroutine),
 * the file will not be published (no-op), but no error will occur.
 */
@InternalRoborazziApi
object RoborazziReportingBridge {
  private val currentPublisher = ThreadLocal<((File) -> Unit)?>()

  fun setPublisher(publisher: (File) -> Unit) {
    currentPublisher.set(publisher)
  }

  fun removePublisher() {
    currentPublisher.remove()
  }

  fun publishFile(file: File) {
    currentPublisher.get()?.invoke(file)
  }
}

internal actual fun roborazziReportCapturedImage(absolutePath: String) {
  RoborazziReportingBridge.publishFile(File(absolutePath))
}
