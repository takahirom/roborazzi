package io.github.takahirom.roborazzi

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
import com.github.takahirom.roborazzi.AnnotationFilter
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi

open class GenerateComposePreviewRobolectricTestsExtension @Inject constructor(objects: ObjectFactory) {
  companion object {
    internal const val DEFAULT_TESTER_CLASS = "com.github.takahirom.roborazzi.AndroidComposePreviewTester"
  }

  val enable: Property<Boolean> = objects.property(Boolean::class.java)
    .convention(false)

  /**
   * Experimental rendering scale for generated Compose Preview Robolectric tests.
   *
   * The scale is applied to the device density before Compose content is rendered, so the
   * preview's logical dp dimensions are preserved while the surface pixel dimensions scale
   * accordingly. This is independent of capture-time `resizeScale`, which resizes the captured
   * bitmap. Density-qualified resources may resolve differently at the scaled density.
   * Must be finite and positive. The resulting dpi is rounded to the nearest integer and
   * clamped to a minimum of 1 dpi.
   */
  @ExperimentalRoborazziApi
  val renderScale: Property<Double> = objects.property(Double::class.java).convention(1.0)

  /**
   * The package names to scan for the Composable Previews.
   */
  val packages: ListProperty<String> = objects.listProperty(String::class.java)

  /**
   * If true, the private previews will be included in the test.
   */
  val includePrivatePreviews: Property<Boolean> = objects.property(Boolean::class.java)
    .convention(false)

  /**
   * [robolectricConfig] will be passed to the Robolectric's @Config annotation in the generated test class.
   * See https://robolectric.org/configuring/ for more information.
   */
  val robolectricConfig: MapProperty<String, String> =
    objects.mapProperty(String::class.java, String::class.java)
      .convention(
        mapOf(
          "sdk" to "[33]",
          "qualifiers" to "RobolectricDeviceQualifiers.Pixel4a",
        )
      )

  /**
   * The fully qualified class name of the custom test class that implements [com.github.takahirom.roborazzi.ComposePreviewTester].
   * This is advanced usage. You can implement your own test class that implements [com.github.takahirom.roborazzi.ComposePreviewTester].
   */
  val testerQualifiedClassName: Property<String> = objects.property(String::class.java)
    .convention(DEFAULT_TESTER_CLASS)

  /**
   * If true, the scan options (like includePrivatePreviews) will be passed to the custom tester via scanOptions.
   * If false (default), these options cannot be set when using a custom tester, and you must configure them directly in your tester implementation.
   */
  val useScanOptionParametersInTester: Property<Boolean> = objects.property(Boolean::class.java)
    .convention(false)

  /**
   * The number of test classes to generate.
   * By default, this is automatically set to match the test task's maxParallelForks value.
   * Set this to match maxParallelForks for parallel test execution.
   *
   * When generatedTestClassCount = 1, generates a single test class.
   * When generatedTestClassCount > 1, generates multiple test classes (RoborazziPreviewParameterizedTests0, Tests1, etc.)
   */
  val generatedTestClassCount: Property<Int> = objects.property(Int::class.java)

  /**
   * Filter for composable previews by annotation. When unset, the plugin defaults to
   * [AnnotationFilter.Filter.RoboPreviewExclude] so `@RoboPreviewExclude` works out of the box.
   * Set explicitly to switch to an opt-in [AnnotationFilter.Include] policy.
   */
  @ExperimentalRoborazziApi
  val annotationFilter: Property<AnnotationFilter> = objects.property(AnnotationFilter::class.java)
}

@CacheableTask
abstract class GenerateComposePreviewRobolectricTestsTask : DefaultTask() {
  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @get:Input
  var scanPackageTrees: ListProperty<String> = project.objects.listProperty(String::class.java)

  @get:Input
  abstract val includePrivatePreviews: Property<Boolean>

  @get:Input
  abstract val testerQualifiedClassName: Property<String>

  @get:Input
  abstract val robolectricConfig: MapProperty<String, String>

  @get:Input
  abstract val generatedTestClassCount: Property<Int>

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

    val packagesExpr = scanPackageTrees.get().joinToString(", ") { "\"$it\"" }
    val includePrivatePreviewsExpr = includePrivatePreviews.get()
    val annotationFilterExpr = when (val filter = annotationFilter.orNull) {
      is AnnotationFilter.Exclude -> "AnnotationFilter.Exclude(${filter.annotations.joinToString(", ") { "\"$it\"" }})"
      is AnnotationFilter.Include -> "AnnotationFilter.Include(${filter.annotations.joinToString(", ") { "\"$it\"" }})"
      null -> "null"
    }
    val testClassCount = generatedTestClassCount.get()

    require(testClassCount >= 1) {
      "generatedTestClassCount must be >= 1, but was $testClassCount"
    }

    val generatedClassFQDN = "com.github.takahirom.roborazzi.RoborazziPreviewParameterizedTests"
    val packageName = generatedClassFQDN.substringBeforeLast(".")
    val baseClassName = generatedClassFQDN.substringAfterLast(".")
    val directory = File(testDir, packageName.replace(".", "/"))
    directory.mkdirs()

    // Delete old generated test files to avoid conflicts when changing generatedTestClassCount
    directory.listFiles()?.filter { it.extension == "kt" }?.forEach { it.delete() }
    val robolectricConfigString =
      "@Config(" + robolectricConfig.get().entries.joinToString(", ") { (key, value) ->
        "$key = $value"
      } + ")"
    val testerQualifiedClassNameString = testerQualifiedClassName.get()

    if (testClassCount == 1) {
      generateTestClass(
        directory = directory,
        packageName = packageName,
        className = baseClassName,
        packagesExpr = packagesExpr,
        includePrivatePreviewsExpr = includePrivatePreviewsExpr,
        annotationFilterExpr = annotationFilterExpr,
        robolectricConfigString = robolectricConfigString,
        testerQualifiedClassNameString = testerQualifiedClassNameString,
        renderScale = scale,
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
          robolectricConfigString = robolectricConfigString,
          testerQualifiedClassNameString = testerQualifiedClassNameString,
          renderScale = scale,
          shardIndex = shardIndex,
          totalShards = testClassCount
        )
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
    robolectricConfigString: String,
    testerQualifiedClassNameString: String,
    renderScale: Double,
    shardIndex: Int?,
    totalShards: Int
  ) {
    val renderScaleArgument =
      if (renderScale == 1.0) "" else "\n                            renderScale = $renderScale,"
    val valuesFunction = if (shardIndex == null) {
      "testParameters"
    } else {
      "testParameters.filterIndexed { index, _ -> index % $totalShards == $shardIndex }"
    }

    File(directory, "$className.kt").writeText(
      """
            package $packageName
            import androidx.compose.ui.test.junit4.ComposeContentTestRule
            import org.junit.Rule
            import org.junit.Test
            import org.junit.runner.RunWith
            import org.junit.rules.TestWatcher
            import org.junit.rules.RuleChain
            import org.robolectric.ParameterizedRobolectricTestRunner
            import org.robolectric.annotation.Config
            import org.robolectric.annotation.GraphicsMode
            import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview
            import com.github.takahirom.roborazzi.*
            import org.junit.experimental.categories.Category


            @RunWith(ParameterizedRobolectricTestRunner::class)
            @OptIn(InternalRoborazziApi::class, ExperimentalRoborazziApi::class)
            @GraphicsMode(GraphicsMode.Mode.NATIVE)
            class $className(
                private val testParameter: ComposePreviewTester.TestParameter<Any>,
            ) {
                @Suppress("UNCHECKED_CAST")
                val junit4TestParameter: ComposePreviewTester.TestParameter.JUnit4TestParameter<Any> = testParameter as ComposePreviewTester.TestParameter.JUnit4TestParameter<Any>
                private val tester = getComposePreviewTester("$testerQualifiedClassNameString")
                private val testLifecycleOptions = tester.options().testLifecycleOptions as ComposePreviewTester.Options.JUnit4TestLifecycleOptions
                val composeTestRule: ComposeContentTestRule by lazy {
                  junit4TestParameter.composeTestRule
                }
                @Suppress("UNCHECKED_CAST")
                @get:Rule
                val rule = junit4TestParameter.releaseComposeTestRuleAfter {
                  RuleChain.outerRule(createRoborazziPreviewConfigurationRule(tester, testParameter))
                    .around(testLifecycleOptions.testRuleFactory(composeTestRule))
                }
                
                @Category(RoborazziComposePreviewTestCategory::class)
                @GraphicsMode(GraphicsMode.Mode.NATIVE)
                $robolectricConfigString
                @Test
                fun test() {
                  tester.test(
                    testParameter = testParameter
                  )
                }
                
                companion object {
                    // lazy for performance
                    val testParameters: List<ComposePreviewTester.TestParameter<*>> by lazy {
                        setupDefaultOptions()
                        val tester = getComposePreviewTester("$testerQualifiedClassNameString")
                        tester.testParameters()
                    }
                    @JvmStatic
                    @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
                    fun values(): List<ComposePreviewTester.TestParameter<*>> = $valuesFunction

                    fun setupDefaultOptions() {
                        ComposePreviewTester.defaultOptionsFromPlugin = ComposePreviewTester.Options(
                            scanOptions = ComposePreviewTester.Options.ScanOptions(
                              packages = listOf($packagesExpr),
                              includePrivatePreviews = $includePrivatePreviewsExpr,
                              annotationFilter = $annotationFilterExpr,
                            ),$renderScaleArgument
                        )
                    }
                }
            }
        """.trimIndent()
    )
  }
}

internal fun validateRenderScale(value: Double): Double {
  require(value.isFinite() && value > 0.0) {
    "renderScale must be finite and greater than 0, but was $value"
  }
  return value
}
