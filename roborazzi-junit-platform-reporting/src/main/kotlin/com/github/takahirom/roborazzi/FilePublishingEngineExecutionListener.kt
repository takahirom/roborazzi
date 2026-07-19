package com.github.takahirom.roborazzi

import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.reporting.FileEntry
import org.junit.platform.engine.reporting.ReportEntry

/**
 * Wraps an [EngineExecutionListener] to bridge Roborazzi's captured image files into
 * JUnit Platform's file publishing API.
 *
 * When a leaf test starts, this installs a publisher on [RoborazziReportingBridge].
 * Each file Roborazzi publishes during that test is turned into a [FileEntry] (with a
 * media type guessed from the file extension) and forwarded to
 * [EngineExecutionListener.fileEntryPublished] against the running test's descriptor.
 * When the test finishes, the publisher is removed so files are never attributed to
 * the wrong test.
 */
@OptIn(InternalRoborazziApi::class)
internal class FilePublishingEngineExecutionListener(
  private val delegate: EngineExecutionListener
) : EngineExecutionListener {

  override fun executionStarted(testDescriptor: TestDescriptor) {
    if (testDescriptor.isTest) {
      RoborazziReportingBridge.setPublisher { file ->
        val mediaType = when (file.extension.lowercase()) {
          "png" -> "image/png"
          "gif" -> "image/gif"
          "jpg", "jpeg" -> "image/jpeg"
          "webp" -> "image/webp"
          else -> "application/octet-stream"
        }
        delegate.fileEntryPublished(testDescriptor, FileEntry.from(file.toPath(), mediaType))
      }
    }
    delegate.executionStarted(testDescriptor)
  }

  override fun executionFinished(testDescriptor: TestDescriptor, testExecutionResult: TestExecutionResult) {
    if (testDescriptor.isTest) {
      RoborazziReportingBridge.removePublisher()
    }
    delegate.executionFinished(testDescriptor, testExecutionResult)
  }

  override fun executionSkipped(testDescriptor: TestDescriptor, reason: String?) {
    delegate.executionSkipped(testDescriptor, reason)
  }

  override fun reportingEntryPublished(testDescriptor: TestDescriptor, entry: ReportEntry) {
    delegate.reportingEntryPublished(testDescriptor, entry)
  }

  override fun dynamicTestRegistered(testDescriptor: TestDescriptor) {
    delegate.dynamicTestRegistered(testDescriptor)
  }
}
