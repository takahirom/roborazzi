package io.github.takahirom.roborazzi

import com.android.build.api.variant.Variant
import com.android.build.gradle.TestedExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.testing.Test
import java.io.File
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject

internal const val MIN_COMPOSABLE_PREVIEW_SCANNER_VERSION = "0.7.0"

open class GenerateComposePreviewRobolectricTestsExtension @Inject constructor(objects: ObjectFactory) {
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
    .convention("com.github.takahirom.roborazzi.AndroidComposePreviewTester")

}

fun generateComposePreviewRobolectricTestsIfNeeded(
  project: Project,
  variant: Variant,
  extension: GenerateComposePreviewRobolectricTestsExtension,
  testTaskProvider: TaskCollection<Test>
) {
  if ((extension.enable.orNull) != true) {
    return
  }
  val logger = project.logger
  setupGenerateComposePreviewRobolectricTestsTask(
    project = project,
    variant = variant,
    extension = extension,
    testerQualifiedClassName = extension.testerQualifiedClassName,
    robolectricConfig = extension.robolectricConfig,
    testTaskProvider = testTaskProvider
  )
  project.afterEvaluate {
    // We use afterEvaluate only for verify
    check(variant.unitTest != null) {
      "Roborazzi: Please enable unit tests for the variant '${variant.name}' in the 'build.gradle' file."
    }
    verifyTestConfig(testTaskProvider, logger)
  }
}

private fun setupGenerateComposePreviewRobolectricTestsTask(
  project: Project,
  variant: Variant,
  extension: GenerateComposePreviewRobolectricTestsExtension,
  testerQualifiedClassName: Property<String>,
  robolectricConfig: MapProperty<String, String>,
  testTaskProvider: TaskCollection<Test>
) {
  check(extension.packages.get().orEmpty().isNotEmpty()) {
    "Please set roborazzi.generateComposePreviewRobolectricTests.packages in the generatePreviewTests extension or set roborazzi.generateComposePreviewRobolectricTests.enable = false." +
      "See https://github.com/sergio-sastre/ComposablePreviewScanner?tab=readme-ov-file#how-to-use for more information."
  }

  val generateTestsTask = project.tasks.register(
    "generate${variant.name.capitalize(Locale.ROOT)}ComposePreviewRobolectricTests",
    GenerateComposePreviewRobolectricTestsTask::class.java
  ) {
    // It seems that this directory path is overridden by addGeneratedSourceDirectory.
    // The generated tests will be located in build/JAVA/generate[VariantName]ComposePreviewRobolectricTests.
    it.outputDir.set(project.layout.buildDirectory.dir("generated/roborazzi/preview-screenshot"))
    it.scanPackageTrees.set(extension.packages)
    it.includePrivatePreviews.set(extension.includePrivatePreviews)
    it.testerQualifiedClassName.set(testerQualifiedClassName)
    it.robolectricConfig.set(robolectricConfig)
  }
  // We need to use sources.java here; otherwise, the generate task will not be executed.
  // https://stackoverflow.com/a/76870110/4339442
  variant.unitTest?.sources?.java?.addGeneratedSourceDirectory(
    generateTestsTask,
    GenerateComposePreviewRobolectricTestsTask::outputDir
  )
  // It seems that the addGeneratedSourceDirectory does not affect the inputs.dir and does not invalidate the task.
  testTaskProvider.configureEach {
    it.inputs.dir(generateTestsTask.flatMap { it.outputDir })
  }
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

  @TaskAction
  fun generateTests() {
    val testDir = outputDir.get().asFile
    testDir.mkdirs()

    val packagesExpr = scanPackageTrees.get().joinToString(", ") { "\"$it\"" }
    val includePrivatePreviewsExpr = includePrivatePreviews.get()

    val generatedClassFQDN = "com.github.takahirom.roborazzi.RoborazziPreviewParameterizedTests"
    val packageName = generatedClassFQDN.substringBeforeLast(".")
    val className = generatedClassFQDN.substringAfterLast(".")
    val directory = File(testDir, packageName.replace(".", "/"))
    directory.mkdirs()
    val robolectricConfigString =
      "@Config(" + robolectricConfig.get().entries.joinToString(", ") { (key, value) ->
        "$key = $value"
      } + ")"
    val testerQualifiedClassNameString = testerQualifiedClassName.get()
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
                    fun values(): List<ComposePreviewTester.TestParameter<*>> = testParameters 
                    
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

fun verifyGenerateComposePreviewRobolectricTests(
  project: Project,
  androidExtension: TestedExtension,
  extension: GenerateComposePreviewRobolectricTestsExtension
) {
  val logger = project.logger
  project.afterEvaluate {
    if ((extension.enable.orNull) != true) {
      return@afterEvaluate
    }
    verifyLibraryDependencies(project)
    verifyComposablePreviewScannerVersion(project)
    verifyAndroidConfig(androidExtension, logger)
  }
}

private fun verifyTestConfig(
  testTaskProvider: TaskCollection<Test>,
  logger: Logger
) {
  testTaskProvider.configureEach { testTask ->
    if (testTask.systemProperties["roborazzi.record.filePathStrategy"] != "relativePathFromRoborazziContextOutputDirectory") {
      logger.info(
        "Roborazzi: Please set 'roborazzi.record.filePathStrategy=relativePathFromRoborazziContextOutputDirectory' in the 'gradle.properties' file. " +
          "This is advisable to avoid unnecessary path manipulation. " +
          "Please refer to 'https://github.com/takahirom/roborazzi?tab=readme-ov-file#roborazzirecordfilepathstrategy' for more information."
      )
    }
    if (testTask.systemProperties["robolectric.pixelCopyRenderMode"] != "hardware") {
      val example = """
        android {
          testOptions {
            unitTests {
              isIncludeAndroidResources = true
              all {
                it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"
              }
            }
          }
        }
      """.trimIndent()
      logger.warn(
        "Roborazzi: Please set 'robolectric.pixelCopyRenderMode = hardware' (Robolectric 4.12.2+) in the 'testOptions' block in the 'build.gradle' file. " +
          "This is advisable to avoid issues with the fidelity of the images.\n" +
          "Please refer to 'https://github.com/takahirom/roborazzi?tab=readme-ov-file#q-the-images-taken-from-roborazzi-seem-broken' for more information.\n" +
          "Example:\n$example"
      )
    }
  }
}

private fun verifyLibraryDependencies(
  project: Project
) {
  val dependencies: Set<Pair<String?, String>> = project.configurations.flatMap { it.dependencies }
    .map { it.group to it.name }
    .toSet()
  verifyLibraryDependencies(dependencies)
}

private fun verifyAndroidConfig(androidExtension: TestedExtension, logger: Logger) {
  if (!androidExtension.testOptions.unitTests.isIncludeAndroidResources) {
    logger.warn(
      "Roborazzi: Please set 'android.testOptions.unitTests.isIncludeAndroidResources = true' in the 'build.gradle' file. " +
        "This is advisable to avoid issues with ActivityNotFoundException."
    )
  }
}

private fun verifyLibraryDependencies(
  allDependencies: Set<Pair<String?, String>>
) {
  fun Set<Pair<String?, String>>.checkExists(libraryName: String) {
    val dependencies = this
    val libNameArray = libraryName.split(":")
    if (!dependencies.contains(libNameArray[0] to libNameArray[1])) {
      val configurationNames =
        "'testImplementation'(For Android Project) or 'kotlin.sourceSets.androidUnitTest.dependencies.implementation'(For KMP)"
      error(
        "Roborazzi: Please add the following $configurationNames dependency to the 'dependencies' block in the 'build.gradle' file: '$libraryName' for the $configurationNames configuration.\n" +
          "For your convenience, visit https://www.google.com/search?q=" + URLEncoder.encode(
          "$libraryName version",
          "UTF-8"
        ) + "\n" +
          "testImplementation(\"$libraryName:version\")"
      )
    }
  }

  val requiredLibraries = listOf(
    "io.github.takahirom.roborazzi:roborazzi-compose-preview-scanner-support",
    "junit:junit",
    "org.robolectric:robolectric",
    "io.github.sergio-sastre.ComposablePreviewScanner:android",
  )
  requiredLibraries.forEach { allDependencies.checkExists(it) }
}

private fun verifyComposablePreviewScannerVersion(
  project: Project
) {
  val dependencies = project.configurations.flatMap { it.dependencies }
  val composablePreviewScannerDependency = dependencies.find { 
    it.group == "io.github.sergio-sastre.ComposablePreviewScanner" && it.name == "android" 
  }
  
  if (composablePreviewScannerDependency != null) {
    val declaredVersion = composablePreviewScannerDependency.version
    
    // If declared version is null (common with BOMs/constraints), try to resolve it
    val versionToCheck = declaredVersion ?: run {
      try {
        // Resolve only common test classpaths to minimize work and side effects
        val candidateConfs = project.configurations
          .filter { conf ->
            conf.isCanBeResolved &&
            conf.name.contains("test", ignoreCase = true) &&
            (conf.name.contains("runtimeClasspath", ignoreCase = true) ||
             conf.name.contains("compileClasspath", ignoreCase = true))
          }
        candidateConfs
          .asSequence()
          .mapNotNull { conf ->
            try {
              conf.resolvedConfiguration.firstLevelModuleDependencies
                .find { dep ->
                  dep.moduleGroup == "io.github.sergio-sastre.ComposablePreviewScanner" &&
                  dep.moduleName == "android"
                }
                ?.moduleVersion
            } catch (e: Exception) {
              project.logger.debug("Roborazzi: Failed to resolve ComposablePreviewScanner version from configuration '${conf.name}': ${e.message}", e)
              null
            }
          }
          .firstOrNull()
      } catch (e: Exception) {
        project.logger.debug("Roborazzi: Failed to resolve ComposablePreviewScanner version from any candidate configuration: ${e.message}", e)
        null
      }
    }
    
    if (versionToCheck != null && isVersionLessThan(versionToCheck, MIN_COMPOSABLE_PREVIEW_SCANNER_VERSION)) {
      error(
        "Roborazzi: ComposablePreviewScanner version $MIN_COMPOSABLE_PREVIEW_SCANNER_VERSION or higher is required. " +
        "Current version: $versionToCheck. " +
        "Please update your ComposablePreviewScanner dependency to version $MIN_COMPOSABLE_PREVIEW_SCANNER_VERSION or higher."
      )
    }
  }
}

internal fun isVersionLessThan(currentVersion: String, requiredVersion: String): Boolean {
  val (currentNums, currentQualifier) = parseVersion(currentVersion)
  val (requiredNums, requiredQualifier) = parseVersion(requiredVersion)

  for (i in 0 until maxOf(currentNums.size, requiredNums.size)) {
    val c = currentNums.getOrNull(i) ?: 0
    val r = requiredNums.getOrNull(i) ?: 0
    if (c < r) return true
    if (c > r) return false
  }
  // Numeric parts equal – handle pre-release vs stable
  if (currentQualifier == requiredQualifier) return false
  if (currentQualifier != null && requiredQualifier == null) return true   // pre-release < stable
  if (currentQualifier == null && requiredQualifier != null) return false  // stable !< pre-release
  // Both have qualifiers: fall back to lexicographic compare (best-effort)
  return currentQualifier!! < requiredQualifier!!
}

internal fun parseVersion(version: String): Pair<List<Int>, String?> {
  val parts = version.split("-", limit = 2)
  val numeric = parts[0].split(".").map { it.toIntOrNull() ?: 0 }
  val qualifier = parts.getOrNull(1)?.lowercase(Locale.ROOT)
  return numeric to qualifier
}