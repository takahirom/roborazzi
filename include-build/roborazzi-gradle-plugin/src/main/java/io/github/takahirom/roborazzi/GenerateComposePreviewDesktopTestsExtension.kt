package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.AnnotationFilter
import com.github.takahirom.roborazzi.DesktopPreviewDeviceProfile
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

open class GenerateComposePreviewDesktopTestsExtension @Inject constructor(objects: ObjectFactory) {
  companion object {
    internal const val DEFAULT_TESTER_CLASS =
      "com.github.takahirom.roborazzi.DefaultDesktopComposePreviewTester"
  }

  val enable: Property<Boolean> = objects.property(Boolean::class.java)
    .convention(false)

  /**
   * The package names to scan for the Composable Previews.
   */
  val packages: ListProperty<String> = objects.listProperty(String::class.java)

  /**
   * The name of the Kotlin Multiplatform JVM target to generate the tests for
   * (e.g. "desktop" for `jvm("desktop")`).
   *
   * When the project has exactly one JVM target this can be omitted; with multiple
   * JVM targets it must be set so the tests are not generated into a target that
   * cannot compile them (e.g. a server target without Compose dependencies).
   * Not used for plain JVM (org.jetbrains.kotlin.jvm) projects.
   */
  val targetName: Property<String> = objects.property(String::class.java)

  /**
   * If true, the private previews will be included in the test.
   */
  val includePrivatePreviews: Property<Boolean> = objects.property(Boolean::class.java)
    .convention(false)

  /**
   * The fully qualified class name of the custom test class that implements
   * [com.github.takahirom.roborazzi.DesktopComposePreviewTester].
   */
  val testerQualifiedClassName: Property<String> = objects.property(String::class.java)
    .convention(DEFAULT_TESTER_CLASS)

  /**
   * Acknowledges that a custom tester applies the scan options itself.
   *
   * The scan options are always passed to the tester as `options().scanOptions`, whatever this
   * property is set to. What they cannot do is apply themselves: [includePrivatePreviews] and
   * [annotationFilter] take effect inside `testParameters()`, which a custom tester usually
   * overrides, so the scanner call that would honour them is your code, not the plugin's.
   *
   * To stop that from failing silently, the plugin rejects the combination of a custom tester
   * and those options. Set this to true to state that you read `options().scanOptions` in your
   * own `testParameters()`, and the build proceeds.
   *
   * This has no effect with the default tester.
   */
  val useScanOptionParametersInTester: Property<Boolean> = objects.property(Boolean::class.java)
    .convention(false)

  /**
   * The number of test classes to generate.
   * By default, this is automatically set to match the test task's maxParallelForks value.
   *
   * When generatedTestClassCount = 1, generates a single test class.
   * When generatedTestClassCount > 1, generates multiple test classes
   * (RoborazziDesktopPreviewParameterizedTests0, Tests1, etc.)
   */
  val generatedTestClassCount: Property<Int> = objects.property(Int::class.java)

  /**
   * Filter for composable previews by annotation. When unset, the plugin defaults to
   * [AnnotationFilter.Filter.RoboPreviewExclude] so `@RoboPreviewExclude` works out of the box.
   * Set explicitly to switch to an opt-in [AnnotationFilter.Include] policy.
   */
  @ExperimentalRoborazziApi
  val annotationFilter: Property<AnnotationFilter> = objects.property(AnnotationFilter::class.java)

  /**
   * How previews are sized and scaled by the test task of the target's default test run.
   *
   * Required: there is no default, because the profile decides the size and density of every
   * golden this module records and no value is right for every project. Leaving it unset fails
   * configuration with a message listing the presets.
   *
   * A custom tester reads it as `options().deviceProfile`, so build its options from
   * `DesktopComposePreviewTester.defaultOptionsFromPlugin.copy(...)`: options constructed from
   * scratch drop everything the plugin configured, this profile included.
   *
   * ```kotlin
   * roborazzi.generateComposePreviewDesktopTests {
   *   deviceProfile = DesktopPreviewDeviceProfile.Pixel4a
   * }
   * ```
   */
  @ExperimentalRoborazziApi
  val deviceProfile: Property<DesktopPreviewDeviceProfile> =
    objects.property(DesktopPreviewDeviceProfile::class.java)

  /**
   * Profiles for additional Kotlin test runs of the same target, keyed by test run name.
   *
   * A test run is how one module captures the same previews more than once: the Roborazzi plugin
   * gives every test run of the target its own set of tasks and its own output directory, so the
   * outputs never overwrite each other.
   *
   * ```kotlin
   * kotlin {
   *   jvm("desktop") {
   *     testRuns.create("androidCompat")
   *   }
   * }
   *
   * roborazzi {
   *   // required as soon as there is more than one run
   *   separateOutputDirs = true
   *   generateComposePreviewDesktopTests {
   *     deviceProfileByTestRun.put(
   *       "androidCompat",
   *       DesktopPreviewDeviceProfile.Pixel4a,
   *     )
   *   }
   * }
   * ```
   *
   * Recording the run above writes to `build/outputs/roborazzi/desktopAndroidCompat/`, while the
   * default run keeps writing to `build/outputs/roborazzi/desktop/`.
   */
  @ExperimentalRoborazziApi
  val deviceProfileByTestRun: MapProperty<String, DesktopPreviewDeviceProfile> =
    objects.mapProperty(String::class.java, DesktopPreviewDeviceProfile::class.java)

  /**
   * If true, previews that need the same Compose scene are captured without closing it in between.
   *
   * Opening a scene is a large part of what capturing one preview costs, so sharing it across the
   * previews that can share it is where the time goes. Previews are only grouped when their surface
   * size and locale match; a preview with `manualClockOptions` always gets a scene of its own,
   * because the scene clock cannot be rewound for the next one.
   *
   * This changes the generated test class: it is run by
   * [com.github.takahirom.roborazzi.DesktopPreviewSceneReuseRunner] rather than by JUnit's
   * `Parameterized`, since a scene cannot be held open across test method invocations. Each preview
   * is still reported as its own test, under the same name, so filters and reports do not change.
   *
   * Off by default while the behaviour settles. Two kinds of customization opt out of it silently
   * on purpose: a custom `Capturer` owns `setContent`, so it cannot share a scene (this logs once
   * and captures a scene per preview as before), and a custom tester that overrides only
   * `test(testParameter)` keeps the per-preview default of `test(testParameters, listener)`. Give a
   * custom tester the list overload if you want it to reuse scenes.
   */
  @ExperimentalRoborazziApi
  val sceneReuse: Property<Boolean> = objects.property(Boolean::class.java)
    .convention(false)

  /**
   * Renders every preview at this fraction of its device density.
   *
   * The preview keeps its logical dp size and the raster surface shrinks with the density, so half
   * the scale is a quarter of the pixels to rasterize. Only values expressed in dp and sp follow
   * the density: anything drawn in raw pixels keeps its absolute size and so appears relatively
   * thicker and shifted in the smaller image.
   *
   * `generateComposePreviewRobolectricTests` has the same option, and the two runtimes round the
   * same way, so a module that captures the same previews on both runtimes can scale both and go
   * on comparing them.
   *
   * With [deviceProfile] left at a profile that has no default device, the desktop runtime has no
   * device density to scale; the pinned `1dp == 1px` density is read as 160dpi and scaled through
   * the same integer dpi, so a scale of 0.5 gives `1dp == 0.5px`.
   *
   * Must be finite and positive. The resulting dpi is rounded to the nearest integer and clamped
   * to a minimum of 1 dpi.
   *
   * A custom [com.github.takahirom.roborazzi.DesktopComposePreviewTester] that sizes its own
   * surface has to pass `options().renderScale` to `DesktopPreviewRenderSpec.resolve`. A tester
   * that drops the value fails the generated test, so no opt-in flag is needed here.
   */
  @ExperimentalRoborazziApi
  val renderScale: Property<Double> = objects.property(Double::class.java).convention(1.0)
}

@CacheableTask
abstract class GenerateComposePreviewDesktopTestsTask : DefaultTask() {
  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @get:Input
  abstract val scanPackageTrees: ListProperty<String>

  @get:Input
  abstract val includePrivatePreviews: Property<Boolean>

  @get:Input
  abstract val testerQualifiedClassName: Property<String>

  @get:Input
  abstract val generatedTestClassCount: Property<Int>

  @get:Input
  abstract val sceneReuse: Property<Boolean>

  @get:Input
  abstract val renderScale: Property<Double>

  @get:Input
  @get:Optional
  @ExperimentalRoborazziApi
  abstract val annotationFilter: Property<AnnotationFilter>

  @TaskAction
  @OptIn(ExperimentalRoborazziApi::class)
  fun generateTests() {
    val scale = validateRenderScale(renderScale.getOrElse(1.0))
    val testDir = outputDir.get().asFile
    testDir.mkdirs()

    val packagesExpr =
      scanPackageTrees.get().joinToString(", ") { "\"${it.escapeForKotlinStringLiteral()}\"" }
    val includePrivatePreviewsExpr = includePrivatePreviews.get()
    val annotationFilterExpr = when (val filter = annotationFilter.orNull) {
      is AnnotationFilter.Exclude -> "AnnotationFilter.Exclude(${filter.annotations.joinToString(", ") { "\"${it.escapeForKotlinStringLiteral()}\"" }})"
      is AnnotationFilter.Include -> "AnnotationFilter.Include(${filter.annotations.joinToString(", ") { "\"${it.escapeForKotlinStringLiteral()}\"" }})"
      null -> "null"
    }
    val testClassCount = generatedTestClassCount.get()

    require(testClassCount >= 1) {
      "generatedTestClassCount must be >= 1, but was $testClassCount"
    }

    val generatedClassFQDN =
      "com.github.takahirom.roborazzi.RoborazziDesktopPreviewParameterizedTests"
    val packageName = generatedClassFQDN.substringBeforeLast(".")
    val baseClassName = generatedClassFQDN.substringAfterLast(".")
    val directory = File(testDir, packageName.replace(".", "/"))
    directory.mkdirs()

    // Delete old generated test files to avoid conflicts when changing
    // generatedTestClassCount. Scoped to this task's class name prefix so files
    // from another generator sharing the directory are never wiped.
    directory.listFiles()
      ?.filter { it.extension == "kt" && it.name.startsWith(baseClassName) }
      ?.forEach { it.delete() }
    val testerQualifiedClassNameString = testerQualifiedClassName.get().escapeForKotlinStringLiteral()

    if (testClassCount == 1) {
      generateTestClass(
        directory = directory,
        packageName = packageName,
        className = baseClassName,
        packagesExpr = packagesExpr,
        includePrivatePreviewsExpr = includePrivatePreviewsExpr,
        annotationFilterExpr = annotationFilterExpr,
        testerQualifiedClassNameString = testerQualifiedClassNameString,
        shardIndex = null,
        totalShards = 1,
        sceneReuse = sceneReuse.get(),
        renderScale = scale
      )
    } else {
      repeat(testClassCount) { shardIndex ->
        generateTestClass(
          directory = directory,
          packageName = packageName,
          className = "$baseClassName$shardIndex",
          packagesExpr = packagesExpr,
          includePrivatePreviewsExpr = includePrivatePreviewsExpr,
          annotationFilterExpr = annotationFilterExpr,
          testerQualifiedClassNameString = testerQualifiedClassNameString,
          shardIndex = shardIndex,
          totalShards = testClassCount,
          sceneReuse = sceneReuse.get(),
          renderScale = scale
        )
      }
    }
  }

  /**
   * Escapes a configured string for embedding in a generated Kotlin string literal.
   * Without this, values like the documented nested annotation name
   * `com.example.Outer$Inner` would be interpreted as string templates and break
   * the generated test's compilation.
   */
  private fun String.escapeForKotlinStringLiteral(): String = buildString {
    for (c in this@escapeForKotlinStringLiteral) {
      when (c) {
        '\\' -> append("\\\\")
        '"' -> append("\\\"")
        '$' -> append("\\$")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> append(c)
      }
    }
  }

  /**
   * Writes the scene-reusing variant of the test class.
   *
   * The class holds configuration only. All the running - collecting the previews, choosing this
   * shard's slice, grouping previews by the scene they need, and reporting each one as its own test
   * - lives in [com.github.takahirom.roborazzi.DesktopPreviewSceneReuseRunner], so the behaviour can
   * be fixed in the library rather than in code generated into every project.
   */
  private fun generateSceneReuseTestClass(
    directory: File,
    packageName: String,
    className: String,
    packagesExpr: String,
    includePrivatePreviewsExpr: Boolean,
    annotationFilterExpr: String,
    testerQualifiedClassNameString: String,
    shardIndex: Int?,
    totalShards: Int,
    renderScale: Double
  ) {
    val renderScaleArgument =
      if (renderScale == 1.0) "" else "\n                            renderScale = $renderScale,"
    File(directory, "$className.kt").writeText(
      """
            package $packageName
            import org.junit.rules.TestRule
            import org.junit.runner.RunWith
            import com.github.takahirom.roborazzi.*


            @RunWith(DesktopPreviewSceneReuseRunner::class)
            @OptIn(InternalRoborazziApi::class, ExperimentalRoborazziApi::class)
            class $className : DesktopPreviewSceneReuseTest {
                override fun createTester(): DesktopComposePreviewTester {
                    setupDefaultOptions()
                    return getDesktopComposePreviewTester("$testerQualifiedClassNameString")
                }

                override fun createTestRule(): TestRule {
                    val testLifecycleOptions = createTester().options().testLifecycleOptions as DesktopComposePreviewTester.Options.JUnit4TestLifecycleOptions
                    return testLifecycleOptions.testRuleFactory()
                }

                override val shardIndex: Int? = $shardIndex

                override val totalShards: Int = $totalShards

                companion object {
                    fun setupDefaultOptions() {
                        DesktopComposePreviewTester.defaultOptionsFromPlugin = DesktopComposePreviewTester.Options(
                            deviceProfile = requireNotNull(roborazziSystemPropertyDesktopDeviceProfile()) {
                              "Roborazzi: no desktop device profile reached the test JVM. The " +
                                "Gradle plugin sets it from " +
                                "generateComposePreviewDesktopTests.deviceProfile, so this means " +
                                "the test task was not configured by the Roborazzi plugin."
                            },
                            sceneReuse = true,$renderScaleArgument
                            scanOptions = DesktopComposePreviewTester.Options.ScanOptions(
                              packages = listOf($packagesExpr),
                              includePrivatePreviews = $includePrivatePreviewsExpr,
                              annotationFilter = $annotationFilterExpr,
                            )
                        )
                    }
                }
            }
        """.trimIndent()
    )
  }

  private fun generateTestClass(
    directory: File,
    packageName: String,
    className: String,
    packagesExpr: String,
    includePrivatePreviewsExpr: Boolean,
    annotationFilterExpr: String,
    testerQualifiedClassNameString: String,
    shardIndex: Int?,
    totalShards: Int,
    sceneReuse: Boolean,
    renderScale: Double
  ) {
    if (sceneReuse) {
      generateSceneReuseTestClass(
        directory = directory,
        packageName = packageName,
        className = className,
        packagesExpr = packagesExpr,
        includePrivatePreviewsExpr = includePrivatePreviewsExpr,
        annotationFilterExpr = annotationFilterExpr,
        testerQualifiedClassNameString = testerQualifiedClassNameString,
        shardIndex = shardIndex,
        totalShards = totalShards,
        renderScale = renderScale
      )
      return
    }
    val renderScaleArgument =
      if (renderScale == 1.0) "" else "\n                            renderScale = $renderScale,"
    // Shards are assigned after sorting by a stable identifier: neither ClassGraph
    // order nor a custom tester's order is guaranteed to be identical across the
    // independently-initialized test JVMs, and index-based sharding on differing
    // orders would drop or duplicate previews.
    val valuesFunction = if (shardIndex == null) {
      "testParameters"
    } else {
      "testParameters.sortedBy { it.toString() }" +
        ".filterIndexed { index, _ -> index % $totalShards == $shardIndex }"
    }

    File(directory, "$className.kt").writeText(
      """
            package $packageName
            import org.junit.Rule
            import org.junit.Test
            import org.junit.rules.TestRule
            import org.junit.runner.RunWith
            import org.junit.runners.Parameterized
            import com.github.takahirom.roborazzi.*


            @RunWith(Parameterized::class)
            @OptIn(InternalRoborazziApi::class, ExperimentalRoborazziApi::class)
            class $className(
                private val testParameter: DesktopPreviewTestParameter,
            ) {
                private val tester = getDesktopComposePreviewTester("$testerQualifiedClassNameString")
                private val testLifecycleOptions = tester.options().testLifecycleOptions as DesktopComposePreviewTester.Options.JUnit4TestLifecycleOptions

                @get:Rule
                val rule: TestRule = testLifecycleOptions.testRuleFactory()

                @Test
                fun test() {
                  DesktopRenderScaleVerification.beforeTest()
                  tester.test(testParameter)
                  DesktopRenderScaleVerification.afterTest(tester)
                }

                companion object {
                    // lazy for performance
                    val testParameters: List<DesktopPreviewTestParameter> by lazy {
                        setupDefaultOptions()
                        val tester = getDesktopComposePreviewTester("$testerQualifiedClassNameString")
                        tester.testParameters()
                    }
                    @JvmStatic
                    @Parameterized.Parameters(name = "{0}")
                    fun values(): List<DesktopPreviewTestParameter> = $valuesFunction

                    fun setupDefaultOptions() {
                        DesktopComposePreviewTester.defaultOptionsFromPlugin = DesktopComposePreviewTester.Options(
                            deviceProfile = requireNotNull(roborazziSystemPropertyDesktopDeviceProfile()) {
                              "Roborazzi: no desktop device profile reached the test JVM. The " +
                                "Gradle plugin sets it from " +
                                "generateComposePreviewDesktopTests.deviceProfile, so this means " +
                                "the test task was not configured by the Roborazzi plugin."
                            },$renderScaleArgument
                            scanOptions = DesktopComposePreviewTester.Options.ScanOptions(
                              packages = listOf($packagesExpr),
                              includePrivatePreviews = $includePrivatePreviewsExpr,
                              annotationFilter = $annotationFilterExpr,
                            )
                        )
                    }
                }
            }
        """.trimIndent()
    )
  }
}
