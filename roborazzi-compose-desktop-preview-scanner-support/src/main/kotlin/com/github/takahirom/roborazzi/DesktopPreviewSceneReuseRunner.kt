package com.github.takahirom.roborazzi

import org.junit.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.Runner
import org.junit.runner.manipulation.Filter
import org.junit.runner.manipulation.Filterable
import org.junit.runner.manipulation.NoTestsRemainException
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunNotifier
import org.junit.runners.model.Statement

/**
 * What [DesktopPreviewSceneReuseRunner] needs from the class the plugin generates.
 *
 * The generated class carries only the configuration; the runner holds the logic, so that turning
 * scene reuse on does not mean generating a second body of code to keep in step with this one.
 */
@ExperimentalRoborazziApi
interface DesktopPreviewSceneReuseTest {
  /**
   * Builds a tester. Called more than once: collecting the previews and capturing them use
   * separate instances, the same way the parameterized tests do.
   */
  fun createTester(): DesktopComposePreviewTester

  /** Builds the [TestRule] to wrap one capture in, or null when the project configured none. */
  fun createTestRule(): TestRule?

  /** This class's shard, or null when the module generates a single test class. */
  val shardIndex: Int?

  /** How many classes the previews were split across. */
  val totalShards: Int
}

/**
 * Runs a shard's previews with the scenes shared inside each configuration group.
 *
 * JUnit's `Parameterized` runner cannot do this: it runs one test method per preview, and the
 * Compose scene has to stay open across several of them. `SkikoComposeUiTest.runTest` creates and
 * closes its scene inside one call (`withScene` is a try/finally around the whole block), so there
 * is no way to hold a scene open across method invocations - the run has to be inverted, with one
 * call capturing a whole group.
 *
 * What this runner is careful to keep is per-preview reporting. Each preview gets its own
 * [Description], named the way `Parameterized` names it, so `--tests` filters, report diffs and
 * rerun-by-name keep working, and a preview that fails its comparison is reported on its own
 * without ending the group it shares a scene with.
 */
@ExperimentalRoborazziApi
class DesktopPreviewSceneReuseRunner(private val testClass: Class<*>) : Runner(), Filterable {

  private val configuration: DesktopPreviewSceneReuseTest =
    testClass.getDeclaredConstructor().newInstance() as DesktopPreviewSceneReuseTest

  private var parameters: List<DesktopPreviewTestParameter> = run {
    val tester = configuration.createTester()
    shardOfDesktopPreviews(
      testParameters = tester.testParameters(),
      profile = tester.options().deviceProfile,
      renderScale = tester.options().renderScale,
      shardIndex = configuration.shardIndex,
      totalShards = configuration.totalShards,
    )
  }

  // Mirrors Parameterized's "test[<parameter>]", so a filter written for the non-reusing runner
  // still selects the same previews.
  private fun descriptionFor(testParameter: DesktopPreviewTestParameter): Description =
    Description.createTestDescription(testClass, "test[$testParameter]")

  override fun getDescription(): Description {
    val suite = Description.createSuiteDescription(testClass)
    parameters.forEach { suite.addChild(descriptionFor(it)) }
    return suite
  }

  override fun filter(filter: Filter) {
    parameters = parameters.filter { filter.shouldRun(descriptionFor(it)) }
    if (parameters.isEmpty()) throw NoTestsRemainException()
  }

  @OptIn(InternalRoborazziApi::class)
  override fun run(notifier: RunNotifier) {
    if (parameters.isEmpty()) return
    // Once per class rather than per preview: with scene reuse on a whole group is resolved
    // before its first capture runs, so clearing between captures would discard the records of
    // the previews behind it.
    DesktopRenderScaleVerification.beforeTest()
    val verificationTester = configuration.createTester()
    // DesktopPreviewTestParameter has no equals, so this is identity based, which is what is
    // wanted: two variations of one preview are two entries.
    val reported = mutableSetOf<DesktopPreviewTestParameter>()

    val listener = DesktopPreviewCaptureListener { testParameter, capture ->
      val description = descriptionFor(testParameter)
      reported.add(testParameter)
      notifier.fireTestStarted(description)
      try {
        val statement = object : Statement() {
          override fun evaluate() = capture()
        }
        // A fresh rule per preview, so a TestWatcher or a retry rule sees one test per preview
        // rather than one per scene.
        (configuration.createTestRule()?.apply(statement, description) ?: statement).evaluate()
        DesktopRenderScaleVerification.afterTest(verificationTester)
      } catch (assumptionViolated: AssumptionViolatedException) {
        // A preview that decides it does not apply - Assume.assumeTrue in a rule, or a tester that
        // skips a configuration - is a skip on every other runner, so it has to be one here too
        // rather than turning into a red build.
        notifier.fireTestAssumptionFailed(Failure(description, assumptionViolated))
      } catch (throwable: Throwable) {
        notifier.fireTestFailure(Failure(description, throwable))
      } finally {
        notifier.fireTestFinished(description)
      }
    }

    var sceneFailure: Throwable? = null
    try {
      configuration.createTester().test(parameters, listener)
    } catch (throwable: Throwable) {
      sceneFailure = throwable
    }

    // Anything the tester never reached - because opening the scene failed, or because a custom
    // tester quietly skipped it - is reported as a failure rather than disappearing from the run.
    val unreported = parameters.filterNot { it in reported }
    unreported.forEach { testParameter ->
      val description = descriptionFor(testParameter)
      notifier.fireTestStarted(description)
      notifier.fireTestFailure(
        Failure(
          description,
          sceneFailure ?: IllegalStateException(
            "Roborazzi: the tester returned without capturing $testParameter."
          )
        )
      )
      notifier.fireTestFinished(description)
    }

    // Closing the scene happens after the last preview has been reported, so a failure there has
    // no preview left to hang it on. JUnit reports what a failing @AfterClass does against the
    // class itself, and so does this: without it the build would be green with the images missing.
    if (sceneFailure != null && unreported.isEmpty()) {
      notifier.fireTestFailure(Failure(Description.createSuiteDescription(testClass), sceneFailure))
    }
  }
}

/**
 * Picks this class's slice of [testParameters], keeping previews that share a scene together.
 *
 * The parameters are sorted first, for the same reason the non-reusing runner sorts them: neither
 * ClassGraph order nor a custom tester's order is guaranteed to be identical across the
 * independently-initialized test JVMs, and index-based sharding on differing orders would drop or
 * duplicate previews. The sort leads with the scene key so that previews needing the same scene end
 * up next to each other, and the slices are then contiguous rather than round-robin - round-robin
 * would deal one preview to each shard and leave every group split into single previews, which is
 * the one thing scene reuse must not do.
 *
 * A group larger than a slice is still split across shards. Each shard reuses its own scene for its
 * own part of it, and every preview is captured exactly once.
 */
internal fun shardOfDesktopPreviews(
  testParameters: List<DesktopPreviewTestParameter>,
  profile: DesktopPreviewDeviceProfile,
  renderScale: Double = 1.0,
  shardIndex: Int?,
  totalShards: Int,
): List<DesktopPreviewTestParameter> {
  // Resolving a device spec is not free, and a comparator is called O(n log n) times, so the keys
  // are computed once each.
  val keys = testParameters.associateWith { desktopPreviewSceneKey(it, profile, renderScale) }
  val sorted = testParameters.sortedWith(
    compareBy(
      { keys.getValue(it).surfaceWidth },
      { keys.getValue(it).surfaceHeight },
      { keys.getValue(it).locale },
      { !keys.getValue(it).reusable },
      { it.toString() },
    )
  )
  if (shardIndex == null) return sorted

  // Spread the remainder over the first shards, so the largest shard is never more than one
  // preview bigger than the smallest.
  val base = sorted.size / totalShards
  val remainder = sorted.size % totalShards
  val start = base * shardIndex + minOf(shardIndex, remainder)
  val size = base + if (shardIndex < remainder) 1 else 0
  return sorted.subList(start, start + size)
}
