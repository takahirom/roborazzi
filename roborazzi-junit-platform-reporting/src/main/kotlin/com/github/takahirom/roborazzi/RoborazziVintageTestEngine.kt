package com.github.takahirom.roborazzi

import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestEngine
import org.junit.platform.engine.UniqueId
import org.junit.vintage.engine.VintageTestEngine
import java.util.Optional
import java.util.logging.Logger

/**
 * A [TestEngine] that delegates to JUnit Vintage's [VintageTestEngine] and, during
 * execution, wraps the [ExecutionRequest]'s listener with a
 * [FilePublishingEngineExecutionListener]. That listener arms a per-test sink on
 * [RoborazziReportingBridge] for the duration of each leaf test so Roborazzi can
 * publish captured images via `EngineExecutionListener.fileEntryPublished()`, which
 * Gradle 9.4+ attaches to its test report as "additional test data".
 *
 * This class is public only so it can be registered and instantiated via the JUnit
 * Platform `ServiceLoader` mechanism (`META-INF/services`). Constructing it
 * programmatically is not supported.
 *
 * ## Setup
 *
 * Register this engine and, crucially, exclude the stock `junit-vintage` engine so
 * your JUnit4/Robolectric tests are executed by this engine only:
 *
 * ```groovy
 * // build.gradle
 * dependencies {
 *   testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-platform-reporting:<version>")
 * }
 * tasks.withType(Test).configureEach {
 *   useJUnitPlatform {
 *     // Required: without this, junit-vintage ALSO discovers and runs the same
 *     // tests, so every test executes twice.
 *     excludeEngines("junit-vintage")
 *   }
 * }
 * ```
 *
 * ## Why the engine id is `roborazzi-vintage`
 *
 * The `junit-` prefix is reserved by the JUnit team for official engines, so this
 * engine uses the id `roborazzi-vintage`. Because the id differs from
 * `junit-vintage`, `excludeEngines("junit-vintage")` filters out the stock engine
 * while keeping this one active — which is exactly what avoids double execution.
 *
 * ## Parallel execution
 *
 * The reporting bridge uses a single JVM-global sink, so image attachment only works
 * when tests run sequentially within a worker. If Vintage parallel execution
 * (`junit.vintage.execution.parallel.enabled=true`) is configured, image attachment is
 * disabled (tests still run) to avoid attributing images to the wrong test.
 */
@OptIn(InternalRoborazziApi::class)
class RoborazziVintageTestEngine : TestEngine {
  private val delegate = VintageTestEngine()

  override fun getId(): String = ENGINE_ID

  override fun getGroupId(): Optional<String> = Optional.of("io.github.takahirom.roborazzi")

  override fun getArtifactId(): Optional<String> = Optional.of("roborazzi-junit-platform-reporting")

  /**
   * The version reported comes from the jar manifest's `Implementation-Version`
   * attribute; it is empty when running from a source/classes directory with no manifest.
   */
  override fun getVersion(): Optional<String> =
    Optional.ofNullable(javaClass.`package`?.implementationVersion)

  override fun discover(discoveryRequest: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
    return delegate.discover(discoveryRequest, uniqueId)
  }

  override fun execute(request: ExecutionRequest) {
    val parallelEnabled = request.configurationParameters
      .get("junit.vintage.execution.parallel.enabled")
      .orElse("false")
      .toBoolean()
    if (parallelEnabled) {
      logger.warning(
        "Roborazzi: junit.vintage.execution.parallel.enabled=true, so captured image " +
          "attachment is disabled (a per-test sink would be interleaved across parallel " +
          "tests). Tests still run normally."
      )
      // Run without wrapping so we never arm the shared sink under parallel execution.
      delegate.execute(request)
      return
    }
    // Use the official ExecutionRequest.create(...) factory (Platform 1.11+) rather than
    // the deprecated public constructor, and carry the original request's
    // outputDirectoryProvider through so nothing downstream (e.g. Gradle's file
    // publishing) loses it.
    val wrappedRequest = ExecutionRequest.create(
      request.rootTestDescriptor,
      FilePublishingEngineExecutionListener(request.engineExecutionListener),
      request.configurationParameters,
      request.outputDirectoryProvider
    )
    try {
      delegate.execute(wrappedRequest)
    } finally {
      // Defensively disarm the sink so an abnormal exit never leaves an armed sink for a
      // subsequent engine/test in the same JVM.
      RoborazziReportingBridge.removeSink()
    }
  }

  private companion object {
    private val logger: Logger = Logger.getLogger(RoborazziVintageTestEngine::class.java.name)
  }
}

// `junit-` is reserved for official engines, so we cannot reuse `junit-vintage`.
private const val ENGINE_ID = "roborazzi-vintage"
