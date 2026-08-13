package com.github.takahirom.roborazzi

/**
 * Bridge for publishing captured image files (golden / actual / compare) as test
 * metadata so an external reporting integration can attach them to test reports.
 *
 * When a custom TestEngine (e.g. RoborazziVintageTestEngine from the
 * `roborazzi-junit-platform-reporting` module) is on the classpath, it *arms* a per-test
 * sink via [setSink] before each test and drains it afterwards via [removeSink],
 * forwarding every collected path to JUnit Platform's
 * `EngineExecutionListener.fileEntryPublished()`, which Gradle 9.4+ renders as test
 * attachments. When no engine wrapper is present, no sink is armed and [publishFile] is a
 * no-op.
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
 *  * A `ThreadLocal` would not work either: the engine arms it on the "Test worker"
 *    thread while `captureRoboImage` reads it on the sandbox thread.
 *
 * The only state shared identically by both classloaders and both threads is what the
 * bootstrap classloader owns. [System.getProperties] is a single JVM-global `Properties`
 * (a `Hashtable`), and `String` is bootstrap-loaded, so a `String` value stored there is
 * the same instance seen from every sandbox. We therefore accumulate the captured
 * absolute paths as one newline-separated `String` value rather than a callback or a
 * collection, keeping `roborazzi-core` free of any JUnit Platform types.
 *
 * ## Why a String value (not a collection)
 *
 * [System.getProperties] is a [java.util.Properties], whose contract is that every key
 * and value is a `String`; storing a non-`String` value breaks `Properties.store()`,
 * `Properties.list()`, and `Properties.stringPropertyNames()`. So the sink value is
 * always a `String`: the empty string means "armed, nothing captured yet", and each
 * published path is appended separated by `\n`. Roborazzi image paths are file system
 * paths that in practice never contain a newline, so splitting the value on `\n` in
 * [removeSink] faithfully recovers the individual paths.
 *
 * ## Threading / lifecycle
 *
 * The engine arms the sink in `executionStarted` and drains + removes it in
 * `executionFinished`, both on the test worker thread. Between those two events the test
 * body runs (on the sandbox thread) and appends the paths it writes. Every mutation is
 * guarded by `synchronized(System.getProperties())`: `Properties` extends `Hashtable`, so
 * that lock is the map's own monitor, and because all classloaders resolve to the single
 * bootstrap `Properties` instance the monitor is shared across them too. Gradle forks a
 * JVM per test worker and runs tests sequentially within it, so a single global sink is
 * sufficient; concurrent in-JVM test execution is not supported. If `captureRoboImage`
 * runs while no sink is armed (no engine wrapper, or a stray background thread after the
 * test finished), [publishFile] is simply a no-op.
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
   * Arms the sink for the current test. The stored value is an empty `String`, which
   * [publishFile] later distinguishes from an unarmed sink (`null`).
   */
  fun setSink() {
    synchronized(System.getProperties()) {
      System.getProperties()[SINK_PROPERTY_KEY] = ""
    }
  }

  /**
   * Removes and returns the paths captured during the test (in publish order), or null
   * if no sink was armed.
   */
  fun removeSink(): List<String>? {
    synchronized(System.getProperties()) {
      return (System.getProperties().remove(SINK_PROPERTY_KEY) as? String)
        ?.split("\n")
        ?.filter { it.isNotEmpty() }
    }
  }

  /**
   * Appends [absolutePath] to the current sink, or does nothing when no sink is armed
   * (no reporting engine, or called outside a test).
   */
  fun publishFile(absolutePath: String) {
    synchronized(System.getProperties()) {
      val current = System.getProperties()[SINK_PROPERTY_KEY] as? String ?: return
      System.getProperties()[SINK_PROPERTY_KEY] =
        if (current.isEmpty()) absolutePath else current + "\n" + absolutePath
    }
  }
}

internal actual fun roborazziReportCapturedImage(absolutePath: String) {
  RoborazziReportingBridge.publishFile(absolutePath)
}
