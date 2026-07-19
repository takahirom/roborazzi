package com.github.takahirom.roborazzi

import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.reporting.FileEntry
import org.junit.platform.engine.reporting.ReportEntry
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Wraps an [EngineExecutionListener] to bridge Roborazzi's captured image files into
 * JUnit Platform's file publishing API.
 *
 * When a leaf test starts, this installs a per-test path sink on
 * [RoborazziReportingBridge]. Roborazzi appends the absolute path of every image it
 * writes during that test to the sink (see [RoborazziReportingBridge] for why a shared
 * string sink is required to cross Robolectric's sandbox thread + classloader boundary).
 * When the test finishes, the sink is drained: each collected file is turned into a
 * [FileEntry] (media type guessed from the extension) and forwarded to
 * [EngineExecutionListener.fileEntryPublished] against that test's descriptor, before the
 * `executionFinished` event is delegated so the files are attributed to the right test.
 */
@OptIn(InternalRoborazziApi::class)
internal class FilePublishingEngineExecutionListener(
  private val delegate: EngineExecutionListener
) : EngineExecutionListener {

  override fun executionStarted(testDescriptor: TestDescriptor) {
    if (testDescriptor.isTest) {
      // CopyOnWriteArrayList is a bootstrap-loaded java.util type and is safe to write
      // from the Robolectric sandbox thread while we read it from the worker thread.
      RoborazziReportingBridge.setSink(CopyOnWriteArrayList())
    }
    delegate.executionStarted(testDescriptor)
  }

  override fun executionFinished(testDescriptor: TestDescriptor, testExecutionResult: TestExecutionResult) {
    if (testDescriptor.isTest) {
      val capturedPaths = RoborazziReportingBridge.removeSink().orEmpty()
      for (path in capturedPaths.distinct()) {
        val file = File(path)
        if (!file.exists()) continue
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
