package com.github.takahirom.roborazzi

import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.reporting.FileEntry
import org.junit.platform.engine.reporting.ReportEntry
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Wraps an [EngineExecutionListener] to bridge Roborazzi's captured image files into
 * JUnit Platform's file publishing API.
 *
 * When a leaf test starts, this arms a per-test path sink on [RoborazziReportingBridge].
 * Roborazzi appends the absolute path of every image it writes during that test to the
 * sink (see [RoborazziReportingBridge] for why a shared string sink is required to cross
 * Robolectric's sandbox thread + classloader boundary). When the test finishes, the sink
 * is drained: each collected file is turned into a [FileEntry] (media type guessed from
 * the extension) and forwarded to [EngineExecutionListener.fileEntryPublished] against
 * that test's descriptor, before the `executionFinished` event is delegated so the files
 * are attributed to the right test.
 */
@OptIn(InternalRoborazziApi::class)
internal class FilePublishingEngineExecutionListener(
  private val delegate: EngineExecutionListener
) : EngineExecutionListener {

  override fun executionStarted(testDescriptor: TestDescriptor) {
    // Arm the sink before signalling the start so any image written during the test body
    // is captured. If the delegate throws we must not leave an armed sink behind for the
    // next test, so disarm it and rethrow.
    if (testDescriptor.isTest) {
      RoborazziReportingBridge.setSink()
    }
    try {
      delegate.executionStarted(testDescriptor)
    } catch (t: Throwable) {
      if (testDescriptor.isTest) {
        RoborazziReportingBridge.removeSink()
      }
      throw t
    }
  }

  override fun executionFinished(testDescriptor: TestDescriptor, testExecutionResult: TestExecutionResult) {
    try {
      if (testDescriptor.isTest) {
        val capturedPaths = RoborazziReportingBridge.removeSink().orEmpty()
        for (path in capturedPaths.distinct()) {
          try {
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
          } catch (t: Throwable) {
            // A failure to publish one image must not fail the test or drop the
            // remaining images; log and continue.
            logger.log(Level.WARNING, "Roborazzi: failed to publish captured image $path", t)
          }
        }
      }
    } finally {
      // Always deliver the finished event even if publishing threw unexpectedly.
      delegate.executionFinished(testDescriptor, testExecutionResult)
    }
  }

  override fun executionSkipped(testDescriptor: TestDescriptor, reason: String?) {
    delegate.executionSkipped(testDescriptor, reason)
  }

  override fun reportingEntryPublished(testDescriptor: TestDescriptor, entry: ReportEntry) {
    delegate.reportingEntryPublished(testDescriptor, entry)
  }

  override fun fileEntryPublished(testDescriptor: TestDescriptor, file: FileEntry) {
    // Forward file entries the delegate/engine publishes on its own so they are not lost.
    delegate.fileEntryPublished(testDescriptor, file)
  }

  override fun dynamicTestRegistered(testDescriptor: TestDescriptor) {
    delegate.dynamicTestRegistered(testDescriptor)
  }

  private companion object {
    private val logger: Logger =
      Logger.getLogger(FilePublishingEngineExecutionListener::class.java.name)
  }
}
