package com.github.takahirom.roborazzi

import androidx.compose.runtime.Composable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener
import org.junit.runner.notification.RunNotifier
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

/**
 * What the runner told JUnit, in the order it told it, so a test can assert on the report a build
 * would show rather than on the runner's internals.
 */
private class RecordedEvents : RunListener() {
  val started = mutableListOf<String>()
  val failures = mutableListOf<Pair<String, Throwable>>()
  val assumptionFailures = mutableListOf<String>()
  val finished = mutableListOf<String>()

  override fun testStarted(description: Description) {
    started += description.displayName
  }

  override fun testFailure(failure: Failure) {
    failures += failure.description.displayName to failure.exception
  }

  override fun testAssumptionFailure(failure: Failure) {
    assumptionFailures += failure.description.displayName
  }

  override fun testFinished(description: Description) {
    finished += description.displayName
  }
}

private fun runAndRecord(testClass: Class<*>): RecordedEvents {
  val events = RecordedEvents()
  val notifier = RunNotifier().apply { addListener(events) }
  DesktopPreviewSceneReuseRunner(testClass).run(notifier)
  return events
}

@OptIn(ExperimentalRoborazziApi::class)
internal fun fakeParameter(name: String) = DesktopPreviewTestParameter(
  preview = object : ComposablePreview<AndroidPreviewInfo> {
    override val previewInfo = AndroidPreviewInfo()
    override val previewIndex: Int? = null
    override val previewIndexDisplayName: String? = null
    override val otherAnnotationsInfo = null
    override val declaringClass = "com.example.Previews"
    override val methodName = name
    override val methodParametersType = ""

    @Composable
    override fun invoke() = Unit

    override fun toString() = name
  },
)

/** A tester whose whole-shard overload is driven by [behaviour], so a test can script a scene. */
@OptIn(ExperimentalRoborazziApi::class)
internal open class ScriptedTester(
  private val behaviour: (List<DesktopPreviewTestParameter>, DesktopPreviewCaptureListener) -> Unit,
) : DesktopComposePreviewTester {
  // The runner reads the options before it runs anything, and there is no plugin here to have
  // installed them, so the tester answers for itself.
  override fun options(): DesktopComposePreviewTester.Options =
    DesktopComposePreviewTester.Options(deviceProfile = DesktopPreviewDeviceProfile.Desktop)

  override fun testParameters(): List<DesktopPreviewTestParameter> =
    listOf(fakeParameter("a"), fakeParameter("b"))

  override fun test(testParameter: DesktopPreviewTestParameter) = Unit

  override fun test(
    testParameters: List<DesktopPreviewTestParameter>,
    listener: DesktopPreviewCaptureListener,
  ) = behaviour(testParameters, listener)
}

@OptIn(ExperimentalRoborazziApi::class)
abstract class ScriptedSceneReuseTest internal constructor(
  private val behaviour: (List<DesktopPreviewTestParameter>, DesktopPreviewCaptureListener) -> Unit,
) : DesktopPreviewSceneReuseTest {
  override fun createTester(): DesktopComposePreviewTester = ScriptedTester(behaviour)
  override fun createTestRule(): TestRule? = null
  override val shardIndex: Int? = null
  override val totalShards: Int = 1
}

/** Every preview is captured, and then closing the scene fails - a failing `onDispose`. */
@OptIn(ExperimentalRoborazziApi::class)
class SceneFailsAfterEveryCapture : ScriptedSceneReuseTest({ parameters, listener ->
  parameters.forEach { parameter -> listener.aroundCapture(parameter) {} }
  throw IllegalStateException("dispose failed")
})

/** A preview that decides it does not apply, the way `Assume.assumeTrue` does. */
@OptIn(ExperimentalRoborazziApi::class)
class SceneWithAnAssumptionViolation : ScriptedSceneReuseTest({ parameters, listener ->
  parameters.forEach { parameter ->
    listener.aroundCapture(parameter) {
      if (parameter.preview.methodName == "a") {
        throw org.junit.AssumptionViolatedException("not applicable here")
      }
    }
  }
})

/** The scene never opens, so no preview is captured. */
@OptIn(ExperimentalRoborazziApi::class)
class SceneFailsBeforeAnyCapture : ScriptedSceneReuseTest({ _, _ ->
  throw IllegalStateException("scene failed to open")
})

@OptIn(ExperimentalRoborazziApi::class)
class DesktopPreviewSceneReuseRunnerTest {

  @Before
  fun setUp() {
    // The runner's render scale check reads the options the generated test installs, and they have
    // no default to fall back on, so the test installs them the way that test would.
    DesktopComposePreviewTester.defaultOptionsFromPlugin =
      DesktopComposePreviewTester.Options(deviceProfile = DesktopPreviewDeviceProfile.Desktop)
  }

  @Test
  fun `a scene failure after the last capture is still reported`() {
    val events = runAndRecord(SceneFailsAfterEveryCapture::class.java)

    // Both previews passed, so there is no unreported preview left to hang the failure on. Without
    // somewhere to report it the exception from closing the scene would disappear and the build
    // would be green.
    assertEquals(listOf("a", "b"), events.started.map { it.substringAfter("test[").substringBefore("]") })
    assertEquals(1, events.failures.size)
    assertEquals("dispose failed", events.failures.single().second.message)
    assertTrue(
      "Expected the scene failure to be reported against the class, but was " +
        events.failures.single().first,
      events.failures.single().first.contains(SceneFailsAfterEveryCapture::class.java.name),
    )
  }

  @Test
  fun `a preview that violates an assumption is skipped, not failed`() {
    val events = runAndRecord(SceneWithAnAssumptionViolation::class.java)

    assertEquals(emptyList<String>(), events.failures.map { it.first })
    assertEquals(1, events.assumptionFailures.size)
    assertTrue(events.assumptionFailures.single().contains("test[a]"))
    // The preview still opens and closes, so a report shows it rather than losing it.
    assertEquals(2, events.finished.size)
  }

  @Test
  fun `a scene that never opens fails every preview it should have captured`() {
    val events = runAndRecord(SceneFailsBeforeAnyCapture::class.java)

    assertEquals(2, events.failures.size)
    assertTrue(events.failures.all { it.second.message == "scene failed to open" })
  }
}
