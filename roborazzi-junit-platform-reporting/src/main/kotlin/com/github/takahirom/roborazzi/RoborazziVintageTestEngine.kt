package com.github.takahirom.roborazzi

import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestEngine
import org.junit.platform.engine.UniqueId
import org.junit.vintage.engine.VintageTestEngine
import java.util.Optional

/**
 * A [TestEngine] that delegates to JUnit Vintage's [VintageTestEngine] and, during
 * execution, wraps the [ExecutionRequest]'s listener with a
 * [FilePublishingEngineExecutionListener]. That listener installs a publisher on
 * [RoborazziReportingBridge] for the duration of each leaf test so Roborazzi can
 * publish captured images via `EngineExecutionListener.fileEntryPublished()`, which
 * Gradle 9.4+ attaches to its test report as "additional test data".
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
 */
class RoborazziVintageTestEngine : TestEngine {
  private val delegate = VintageTestEngine()

  override fun getId(): String = ENGINE_ID

  override fun getGroupId(): Optional<String> = delegate.groupId

  override fun getArtifactId(): Optional<String> = delegate.artifactId

  override fun getVersion(): Optional<String> = delegate.version

  override fun discover(discoveryRequest: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
    return delegate.discover(discoveryRequest, uniqueId)
  }

  override fun execute(request: ExecutionRequest) {
    @Suppress("DEPRECATION")
    val wrappedRequest = ExecutionRequest(
      request.rootTestDescriptor,
      FilePublishingEngineExecutionListener(request.engineExecutionListener),
      request.configurationParameters
    )
    delegate.execute(wrappedRequest)
  }
}

// `junit-` is reserved for official engines, so we cannot reuse `junit-vintage`.
private const val ENGINE_ID = "roborazzi-vintage"
