package io.github.takahirom.roborazzi

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

open class GenerateComposePreviewRobolectricTestsExtension @Inject constructor(objects: ObjectFactory) {
  companion object {
    internal const val DEFAULT_TESTER_CLASS = "com.github.takahirom.roborazzi.AndroidComposePreviewTester"
  }

  val enable: Property<Boolean> = objects.property(Boolean::class.java)
    .convention(false)

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
}

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

  @TaskAction
  fun generateTests() {
    val testDir = outputDir.get().asFile
    testDir.mkdirs()

    val packagesExpr = scanPackageTrees.get().joinToString(", ") { "\"$it\"" }
    val includePrivatePreviewsExpr = includePrivatePreviews.get()
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
        robolectricConfigString = robolectricConfigString,
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
          robolectricConfigString = robolectricConfigString,
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
    robolectricConfigString: String,
    testerQualifiedClassNameString: String,
    shardIndex: Int?,
    totalShards: Int
  ) {
    val valuesFunction = if (shardIndex == null) {
      "testParameters"
    } else {
      "testParameters.filterIndexed { index, _ -> index % $totalShards == $shardIndex }"
    }

    File(directory, "$className.kt").writeText(
      """
            package $packageName
            import androidx.activity.ComponentActivity
            import androidx.compose.ui.test.junit4.AndroidComposeTestRule
            import androidx.compose.ui.test.junit4.ComposeContentTestRule
            import androidx.compose.ui.test.junit4.createComposeRule
            import androidx.test.ext.junit.rules.ActivityScenarioRule
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
                val composeTestRule: AndroidComposeTestRule<ActivityScenarioRule<out ComponentActivity>, *> by lazy {
                  junit4TestParameter.composeTestRule
                }
                @Suppress("UNCHECKED_CAST")
                @get:Rule
                val rule = RuleChain.outerRule(
                  testLifecycleOptions.testRuleFactory(composeTestRule)
                )
                
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
                            )
                        )
                    }
                }
            }
        """.trimIndent()
    )
  }
}
