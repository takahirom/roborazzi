package io.github.takahirom.roborazzi

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.variant.HasUnitTest
import com.android.build.api.variant.Variant
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.testing.Test
import java.net.URLEncoder
import java.util.Locale

internal const val MIN_COMPOSABLE_PREVIEW_SCANNER_VERSION = "0.7.0"

/**
 * Android-specific functions for generating Compose preview Robolectric tests.
 * This class is isolated to prevent NoClassDefFoundError when AGP is not on the classpath.
 * It should only be referenced inside withPlugin("com.android.*") blocks.
 */
internal fun generateComposePreviewRobolectricTestsIfNeeded(
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
    check((variant as? HasUnitTest)?.unitTest != null) {
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

  // Auto-detect generatedTestClassCount from maxParallelForks if not explicitly set
  project.afterEvaluate {
    if (!extension.generatedTestClassCount.isPresent) {
      val maxForks = testTaskProvider.mapNotNull { it.maxParallelForks }.maxOrNull() ?: 1
      extension.generatedTestClassCount.convention(maxForks)
    }
  }

  // Validate configuration: check for conflicting settings when using a custom tester
  val isUsingCustomTester = testerQualifiedClassName.get() != GenerateComposePreviewRobolectricTestsExtension.DEFAULT_TESTER_CLASS
  val useScanOptions = extension.useScanOptionParametersInTester.get()
  val includePrivatePreviews = extension.includePrivatePreviews.get()

  if (!useScanOptions && isUsingCustomTester && includePrivatePreviews) {
    throw IllegalArgumentException(
      """
      includePrivatePreviews cannot be set automatically when using a custom tester.

      When using a custom tester, if you override testParameters(), you must manually handle
      the includePrivatePreviews option in your scanner configuration.

      You have two options:
      1. Remove 'includePrivatePreviews = true' from generateComposePreviewRobolectricTests configuration
         and call '.includePrivatePreviews()' directly in your custom tester's testParameters() method.

      2. Set 'useScanOptionParametersInTester = true' in generateComposePreviewRobolectricTests configuration
         and check 'options.scanOptions.includePrivatePreviews' in your testParameters() implementation.

      Example for option 1:
        // In your custom tester:
        override fun testParameters(): List<TestParameter> {
          return AndroidComposablePreviewScanner()
            .scanPackageTrees(*options().scanOptions.packages.toTypedArray())
            .includePrivatePreviews()  // Directly call this
            .getPreviews()
            // ... rest of your implementation
        }

      Example for option 2:
        // In build.gradle.kts:
        generateComposePreviewRobolectricTests {
          enable = true
          packages = listOf("your.package")
          testerQualifiedClassName = "com.example.CustomTester"
          includePrivatePreviews = true
          useScanOptionParametersInTester = true
        }

        // In your custom tester:
        override fun testParameters(): List<TestParameter> {
          val opts = options()
          return AndroidComposablePreviewScanner()
            .scanPackageTrees(*opts.scanOptions.packages.toTypedArray())
            .let {
              if (opts.scanOptions.includePrivatePreviews) {
                it.includePrivatePreviews()  // Conditionally call based on plugin config
              } else {
                it
              }
            }
            .getPreviews()
            // ... rest of your implementation
        }
      """.trimIndent()
    )
  }

  val generateTestsTask = project.tasks.register(
    "generate${variant.name.capitalize(Locale.ROOT)}ComposePreviewRobolectricTests",
    GenerateComposePreviewRobolectricTestsTask::class.java
  ) {
    // It seems that this directory path is overridden by addGeneratedSourceDirectory.
    // The generated tests will be located in build/JAVA/generate[VariantName]ComposePreviewRobolectricTests.
    it.outputDir.set(project.layout.buildDirectory.dir("generated/roborazzi/preview-screenshot/${variant.name}"))
    it.scanPackageTrees.set(extension.packages)
    it.includePrivatePreviews.set(extension.includePrivatePreviews)
    it.testerQualifiedClassName.set(testerQualifiedClassName)
    it.robolectricConfig.set(robolectricConfig)
    it.generatedTestClassCount.set(extension.generatedTestClassCount)
  }
  // AGP 9.0: unitTest is now on HasUnitTest interface, not Variant
  val unitTestSources = (variant as? HasUnitTest)?.unitTest?.sources
  // KMP library (com.android.kotlin.multiplatform.library) requires sources.kotlin
  // because Kotlin compilation doesn't pick up sources added via sources.java
  // https://issuetracker.google.com/issues/259523353
  val isKmpLibrary = project.plugins.hasPlugin("com.android.kotlin.multiplatform.library")
  if (isKmpLibrary) {
    unitTestSources?.kotlin?.addGeneratedSourceDirectory(
      generateTestsTask,
      GenerateComposePreviewRobolectricTestsTask::outputDir
    )
  } else {
    // We need to use sources.java here; otherwise, the generate task will not be executed.
    // https://stackoverflow.com/a/76870110/4339442
    unitTestSources?.java?.addGeneratedSourceDirectory(
      generateTestsTask,
      GenerateComposePreviewRobolectricTestsTask::outputDir
    )
  }
  // It seems that the addGeneratedSourceDirectory does not affect the inputs.dir and does not invalidate the task.
  testTaskProvider.configureEach {
    it.inputs.dir(generateTestsTask.flatMap { it.outputDir })
  }
}

internal fun verifyGenerateComposePreviewRobolectricTestsForAndroid(
  project: Project,
  androidExtension: CommonExtension<*, *, *, *, *, *>,
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

internal fun verifyGenerateComposePreviewRobolectricTestsForKmp(
  project: Project,
  kmpTarget: KotlinMultiplatformAndroidLibraryTarget,
  extension: GenerateComposePreviewRobolectricTestsExtension,
  testTaskProvider: TaskCollection<Test>
) {
  val logger = project.logger
  project.afterEvaluate {
    // KMP library specific check - always check isIncludeAndroidResources
    kmpTarget.compilations.withType(
      com.android.build.api.dsl.KotlinMultiplatformAndroidHostTestCompilation::class.java
    ).all { compilation ->
      if (!compilation.isIncludeAndroidResources) {
        val example = """
          kotlin {
            androidLibrary {
              withHostTest {
                isIncludeAndroidResources = true
              }
            }
          }
        """.trimIndent()
        logger.warn(
          "Roborazzi: Please set 'isIncludeAndroidResources = true' in withHostTest block in the 'build.gradle.kts' file. " +
            "This is advisable to avoid issues with ActivityNotFoundException.\n" +
            "Example:\n$example"
        )
      }
    }

    if ((extension.enable.orNull) != true) {
      return@afterEvaluate
    }
    verifyLibraryDependencies(project)
    verifyComposablePreviewScannerVersion(project)
    verifyTestConfig(testTaskProvider, logger)
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
  verifyLibraryDependenciesInternal(dependencies)
}

private fun verifyAndroidConfig(androidExtension: CommonExtension<*, *, *, *, *, *>, logger: Logger) {
  if (!androidExtension.testOptions.unitTests.isIncludeAndroidResources) {
    logger.warn(
      "Roborazzi: Please set 'android.testOptions.unitTests.isIncludeAndroidResources = true' in the 'build.gradle' file. " +
        "This is advisable to avoid issues with ActivityNotFoundException."
    )
  }
}

private fun verifyLibraryDependenciesInternal(
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

    // If declared version is null (common with BOMs/constraints), skip version verification
    // to avoid configuration-time resolution which causes build performance issues.
    // See: https://github.com/gradle/gradle/issues/2298
    if (declaredVersion == null) {
      project.logger.debug(
        "Roborazzi: ComposablePreviewScanner version check skipped because version is managed by BOM/constraints. " +
        "Please ensure you are using version $MIN_COMPOSABLE_PREVIEW_SCANNER_VERSION or higher."
      )
      return
    }

    if (isVersionLessThan(declaredVersion, MIN_COMPOSABLE_PREVIEW_SCANNER_VERSION)) {
      error(
        "Roborazzi: ComposablePreviewScanner version $MIN_COMPOSABLE_PREVIEW_SCANNER_VERSION or higher is required. " +
          "Current version: $declaredVersion. " +
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
