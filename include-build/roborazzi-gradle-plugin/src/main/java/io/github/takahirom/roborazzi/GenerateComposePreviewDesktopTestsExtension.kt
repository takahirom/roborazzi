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
   *   deviceProfile = DesktopPreviewDeviceProfile.MediumPhone
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
   *       DesktopPreviewDeviceProfile.MediumPhone,
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
  @get:Optional
  @ExperimentalRoborazziApi
  abstract val annotationFilter: Property<AnnotationFilter>

  @TaskAction
  @OptIn(ExperimentalRoborazziApi::class)
  fun generateTests() {
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
        totalShards = 1
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
          totalShards = testClassCount
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

  private fun generateTestClass(
    directory: File,
    packageName: String,
    className: String,
    packagesExpr: String,
    includePrivatePreviewsExpr: Boolean,
    annotationFilterExpr: String,
    testerQualifiedClassNameString: String,
    shardIndex: Int?,
    totalShards: Int
  ) {
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
            public class $className(
                private val testParameter: DesktopPreviewTestParameter,
            ) {
                private val tester = getDesktopComposePreviewTester("$testerQualifiedClassNameString")
                private val testLifecycleOptions = tester.options().testLifecycleOptions as DesktopComposePreviewTester.Options.JUnit4TestLifecycleOptions

                @get:Rule
                public val rule: TestRule = testLifecycleOptions.testRuleFactory()

                @Test
                public fun test() {
                  tester.test(testParameter)
                }

                public companion object {
                    // lazy for performance
                    public val testParameters: List<DesktopPreviewTestParameter> by lazy {
                        setupDefaultOptions()
                        val tester = getDesktopComposePreviewTester("$testerQualifiedClassNameString")
                        tester.testParameters()
                    }
                    @JvmStatic
                    @Parameterized.Parameters(name = "{0}")
                    public fun values(): List<DesktopPreviewTestParameter> = $valuesFunction

                    public fun setupDefaultOptions() {
                        DesktopComposePreviewTester.defaultOptionsFromPlugin = DesktopComposePreviewTester.Options(
                            deviceProfile = requireNotNull(roborazziSystemPropertyDesktopDeviceProfile()) {
                              "Roborazzi: no desktop device profile reached the test JVM. The " +
                                "Gradle plugin sets it from " +
                                "generateComposePreviewDesktopTests.deviceProfile, so this means " +
                                "the test task was not configured by the Roborazzi plugin."
                            },
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
