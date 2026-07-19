package com.github.takahirom.roborazzi

/**
 * Bridge for publishing captured image files (golden / actual / compare) as test
 * metadata so an external reporting integration can attach them to test reports.
 *
 * When a custom TestEngine (e.g. RoborazziVintageTestEngine from the
 * `roborazzi-junit-platform-reporting` module) is on the classpath, it installs a
 * per-test *sink* via [setSink] before each test and drains it afterwards, forwarding
 * every collected path to JUnit Platform's `EngineExecutionListener.fileEntryPublished()`,
 * which Gradle 9.4+ renders as test attachments. When no engine wrapper is present, no
 * sink is installed and [publishFile] is a no-op.
 *
 * ## Why the sink lives in [System.getProperties]
 *
 * Robolectric runs each test inside an *SDK sandbox*: the test body executes on a
 * dedicated sandbox thread (e.g. "SDK 35 Main Thread") AND `roborazzi-core` is reloaded
 * by Robolectric's `SdkSandboxClassLoader`, which is a *different* class from the copy
 * loaded by the Gradle test worker's application classloader. That means:
 *
 *  * A plain `static`/`@Volatile` field would not work: the sandbox sees its own copy of
 *    this object, whose field is never written by the engine (which runs on the worker
 *    classloader).
 *  * A `ThreadLocal` would not work either: the engine sets it on the "Test worker"
 *    thread while `captureRoboImage` reads it on the sandbox thread.
 *
 * The only state shared identically by both classloaders and both threads is what the
 * bootstrap classloader owns. [System.getProperties] is a single JVM-global
 * `Hashtable`, and `java.util.*` collections and `String` are bootstrap-loaded, so a
 * `MutableList<String>` stored there is visible from every sandbox. We therefore pass
 * plain absolute-path strings through that list rather than a callback, keeping
 * `roborazzi-core` free of any JUnit Platform types.
 *
 * ## Threading / lifecycle
 *
 * The engine installs the sink in `executionStarted` and drains + removes it in
 * `executionFinished`, both on the test worker thread. Between those two events the test
 * body runs (on the sandbox thread) and appends the paths it writes. Gradle forks a JVM
 * per test worker and runs tests sequentially within it, so a single global sink is
 * sufficient; concurrent in-JVM test execution is not supported. If `captureRoboImage`
 * runs while no sink is installed (no engine wrapper, or a stray background thread after
 * the test finished), [publishFile] is simply a no-op.
 */
@InternalRoborazziApi
object RoborazziReportingBridge {
  /**
   * Key under which the current test's path sink is stored in [System.getProperties].
   * It is a compile-time constant so the sandbox-loaded copy of this class and the
   * engine's copy resolve to the exact same string.
   */
  const val SINK_PROPERTY_KEY: String = "com.github.takahirom.roborazzi.reportingBridge.sink"

  /**
   * Installs [sink] as the destination for paths published during the current test.
   * The collection must be a bootstrap-loaded `java.util.*` type (see class docs) and
   * should be thread-safe, because it is written from the Robolectric sandbox thread.
   */
  fun setSink(sink: MutableCollection<String>) {
    System.getProperties()[SINK_PROPERTY_KEY] = sink
  }

  /**
   * Removes and returns the current sink (the paths captured during the test), or null
   * if none was installed.
   */
  @Suppress("UNCHECKED_CAST")
  fun removeSink(): MutableCollection<String>? {
    return System.getProperties().remove(SINK_PROPERTY_KEY) as? MutableCollection<String>
  }

  /**
   * Appends [absolutePath] to the current sink, or does nothing when no sink is
   * installed (no reporting engine, or called outside a test).
   */
  @Suppress("UNCHECKED_CAST")
  fun publishFile(absolutePath: String) {
    val sink = System.getProperties()[SINK_PROPERTY_KEY] as? MutableCollection<String> ?: return
    sink.add(absolutePath)
  }
}

internal actual fun roborazziReportCapturedImage(absolutePath: String) {
  RoborazziReportingBridge.publishFile(absolutePath)
}
