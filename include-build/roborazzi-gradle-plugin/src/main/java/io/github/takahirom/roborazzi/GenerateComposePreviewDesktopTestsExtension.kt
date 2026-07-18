package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.AnnotationFilter
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
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
   * If true, the scan options (like includePrivatePreviews) will be passed to the custom tester via scanOptions.
   * If false (default), these options cannot be set when using a custom tester, and you must configure them directly in your tester implementation.
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

    val generatedClassFQDN =
      "com.github.takahirom.roborazzi.RoborazziDesktopPreviewParameterizedTests"
    val packageName = generatedClassFQDN.substringBeforeLast(".")
    val baseClassName = generatedClassFQDN.substringAfterLast(".")
    val directory = File(testDir, packageName.replace(".", "/"))
    directory.mkdirs()

    // Delete old generated test files to avoid conflicts when changing generatedTestClassCount
    directory.listFiles()?.filter { it.extension == "kt" }?.forEach { it.delete() }
    val testerQualifiedClassNameString = testerQualifiedClassName.get()

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
    val valuesFunction = if (shardIndex == null) {
      "previews"
    } else {
      "previews.filterIndexed { index, _ -> index % $totalShards == $shardIndex }"
    }

    File(directory, "$className.kt").writeText(
      """
            package $packageName
            import org.junit.Test
            import org.junit.runner.RunWith
            import org.junit.runners.Parameterized
            import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
            import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview
            import com.github.takahirom.roborazzi.*


            @RunWith(Parameterized::class)
            @OptIn(InternalRoborazziApi::class, ExperimentalRoborazziApi::class)
            class $className(
                private val preview: ComposablePreview<AndroidPreviewInfo>,
            ) {
                private val tester = getDesktopComposePreviewTester("$testerQualifiedClassNameString")

                @Test
                fun test() {
                  tester.test(preview)
                }

                companion object {
                    // lazy for performance
                    val previews: List<ComposablePreview<AndroidPreviewInfo>> by lazy {
                        setupDefaultOptions()
                        val tester = getDesktopComposePreviewTester("$testerQualifiedClassNameString")
                        tester.previews()
                    }
                    @JvmStatic
                    @Parameterized.Parameters(name = "{0}")
                    fun values(): List<ComposablePreview<AndroidPreviewInfo>> = $valuesFunction

                    fun setupDefaultOptions() {
                        DesktopComposePreviewTester.defaultOptionsFromPlugin = DesktopComposePreviewTester.Options(
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
