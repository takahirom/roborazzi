package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.AnnotationFilter
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import java.net.URLEncoder
import java.util.Locale

/**
 * Functions for generating Compose preview tests for the Compose Desktop (JVM) target.
 * Unlike the Robolectric generator this hooks Kotlin JVM targets, so it must not
 * reference any AGP classes.
 */
internal fun generateComposePreviewDesktopTestsForKmpIfNeeded(
  project: Project,
  roborazziExtension: RoborazziExtension,
  kotlinMppExtension: KotlinMultiplatformExtension,
) {
  val extension = roborazziExtension.generateComposePreviewDesktopTests
  project.afterEvaluate {
    if ((extension.enable.orNull) != true) {
      return@afterEvaluate
    }
    val jvmTargets = kotlinMppExtension.targets.filterIsInstance<KotlinJvmTarget>()
    val targetName = extension.targetName.orNull
    val target = when {
      jvmTargets.isEmpty() -> error(
        "Roborazzi: generateComposePreviewDesktopTests requires a Kotlin JVM target " +
          "(e.g. jvm(\"desktop\")), but this project has none."
      )

      targetName != null -> jvmTargets.find { it.name == targetName } ?: error(
        "Roborazzi: generateComposePreviewDesktopTests.targetName is set to '$targetName', " +
          "but the JVM targets of this project are ${jvmTargets.map { it.name }}."
      )

      jvmTargets.size == 1 -> jvmTargets.single()

      else -> error(
        "Roborazzi: This project has multiple Kotlin JVM targets ${jvmTargets.map { it.name }}. " +
          "Please set roborazzi.generateComposePreviewDesktopTests.targetName to the target " +
          "the preview tests should be generated for (the tests need Compose Desktop on the " +
          "test classpath, so generating into every JVM target could break non-UI targets)."
      )
    }
    val testCompilation = target.compilations.findByName("test") ?: error(
      "Roborazzi: The JVM target '${target.name}' has no 'test' compilation."
    )
    setupGenerateComposePreviewDesktopTestsTask(
      project = project,
      roborazziExtension = roborazziExtension,
      variantName = target.name,
      testTaskName = "${target.name}Test",
      testSourceSet = testCompilation.defaultSourceSet,
    )
  }
}

internal fun generateComposePreviewDesktopTestsForJvmIfNeeded(
  project: Project,
  roborazziExtension: RoborazziExtension,
) {
  val extension = roborazziExtension.generateComposePreviewDesktopTests
  project.afterEvaluate {
    if ((extension.enable.orNull) != true) {
      return@afterEvaluate
    }
    val kotlinExtension = project.extensions.getByType(KotlinJvmProjectExtension::class.java)
    setupGenerateComposePreviewDesktopTestsTask(
      project = project,
      roborazziExtension = roborazziExtension,
      variantName = "jvm",
      testTaskName = "test",
      testSourceSet = kotlinExtension.sourceSets.getByName("test"),
    )
  }
}

@OptIn(ExperimentalRoborazziApi::class)
private fun setupGenerateComposePreviewDesktopTestsTask(
  project: Project,
  roborazziExtension: RoborazziExtension,
  variantName: String,
  testTaskName: String,
  testSourceSet: KotlinSourceSet,
) {
  val extension = roborazziExtension.generateComposePreviewDesktopTests
  check(extension.packages.getOrElse(emptyList()).isNotEmpty()) {
    "Please set roborazzi.generateComposePreviewDesktopTests.packages in the generatePreviewTests extension or set roborazzi.generateComposePreviewDesktopTests.enable = false." +
      "See https://github.com/sergio-sastre/ComposablePreviewScanner?tab=readme-ov-file#how-to-use for more information."
  }

  val testTaskProvider: TaskProvider<Test> =
    project.tasks.named(testTaskName, Test::class.java)

  // Auto-detect generatedTestClassCount from maxParallelForks if not explicitly set.
  // Read eagerly (we are in afterEvaluate): mapping the test task provider would add a
  // generate -> test task dependency and create a dependency cycle.
  if (!extension.generatedTestClassCount.isPresent) {
    extension.generatedTestClassCount.convention(testTaskProvider.get().maxParallelForks)
  }

  validateCustomTesterConfiguration(extension)
  warnWhenMixedWithRobolectricPreviewTests(project, roborazziExtension)
  verifyDesktopLibraryDependencies(project)

  val generateTestsTask = project.tasks.register(
    "generate${variantName.capitalize(Locale.ROOT)}ComposePreviewDesktopTests",
    GenerateComposePreviewDesktopTestsTask::class.java
  ) {
    it.outputDir.set(project.layout.buildDirectory.dir("generated/roborazzi/preview-screenshot/$variantName"))
    it.scanPackageTrees.set(extension.packages)
    it.includePrivatePreviews.set(extension.includePrivatePreviews)
    it.testerQualifiedClassName.set(extension.testerQualifiedClassName)
    it.generatedTestClassCount.set(extension.generatedTestClassCount)
    // Unlike the Robolectric generator there is no RoboPreviewExclude default:
    // roborazzi-annotations is an Android library today, so the marker annotations are
    // not usable from commonMain/desktop. Restore the default once roborazzi-annotations
    // is multiplatform.
    it.annotationFilter.set(extension.annotationFilter)
  }
  // Registering the provider as a source directory carries the task dependency,
  // so the generate task runs before the test compilation.
  testSourceSet.kotlin.srcDir(generateTestsTask.flatMap { it.outputDir })
  testTaskProvider.configure {
    it.inputs.dir(generateTestsTask.flatMap { it.outputDir })
  }
}

@OptIn(ExperimentalRoborazziApi::class)
private fun validateCustomTesterConfiguration(extension: GenerateComposePreviewDesktopTestsExtension) {
  val isUsingCustomTester =
    extension.testerQualifiedClassName.get() != GenerateComposePreviewDesktopTestsExtension.DEFAULT_TESTER_CLASS
  val useScanOptions = extension.useScanOptionParametersInTester.get()
  val includePrivatePreviews = extension.includePrivatePreviews.get()

  if (!useScanOptions && isUsingCustomTester && (includePrivatePreviews || extension.annotationFilter.isPresent)) {
    throw IllegalArgumentException(
      """
      includePrivatePreviews / annotationFilter cannot be set automatically when using a custom tester.

      When using a custom tester, if you override previews(), you must manually handle
      the includePrivatePreviews option in your scanner configuration.

      You have two options:
      1. Remove 'includePrivatePreviews = true' / annotationFilter option from generateComposePreviewDesktopTests configuration
         and call '.includePrivatePreviews()' / '.excludeIfAnnotatedWithAnyOf()' / '.includeIfAnnotatedWithAnyOf()' directly in your custom tester's previews() method.

      2. Set 'useScanOptionParametersInTester = true' in generateComposePreviewDesktopTests configuration
         and check 'options().scanOptions.includePrivatePreviews' / 'options().scanOptions.annotationFilter' in your previews() implementation.
      """.trimIndent()
    )
  }
}

@OptIn(ExperimentalRoborazziApi::class)
private fun warnWhenMixedWithRobolectricPreviewTests(
  project: Project,
  roborazziExtension: RoborazziExtension,
) {
  val robolectricEnabled =
    roborazziExtension.generateComposePreviewRobolectricTests.enable.orNull == true
  val separateOutputDirs = roborazziExtension.separateOutputDirs.get()
  if (robolectricEnabled && !separateOutputDirs) {
    project.logger.warn(
      "Roborazzi: Both generateComposePreviewRobolectricTests and generateComposePreviewDesktopTests " +
        "are enabled in this module, and they use the same screenshot names, so the Android and " +
        "desktop screenshots will overwrite each other in the shared output directory. " +
        "Please set 'roborazzi.separateOutputDirs = true' so each task gets its own subdirectory."
    )
  }
}

private fun verifyDesktopLibraryDependencies(project: Project) {
  val dependencies: Set<Pair<String?, String>> = project.configurations.flatMap { it.dependencies }
    .map { it.group to it.name }
    .toSet()

  fun Set<Pair<String?, String>>.checkExists(libraryName: String) {
    val libNameArray = libraryName.split(":")
    if (!contains(libNameArray[0] to libNameArray[1])) {
      val configurationNames =
        "'kotlin.sourceSets.<jvmTarget>Test.dependencies.implementation'(For KMP) or 'testImplementation'(For JVM Project)"
      error(
        "Roborazzi: Please add the following $configurationNames dependency to the 'dependencies' block in the 'build.gradle' file: '$libraryName' for the $configurationNames configuration.\n" +
          "For your convenience, visit https://www.google.com/search?q=" + URLEncoder.encode(
          "$libraryName version",
          "UTF-8"
        ) + "\n" +
          "implementation(\"$libraryName:version\")"
      )
    }
  }

  val requiredLibraries = listOf(
    "io.github.takahirom.roborazzi:roborazzi-compose-desktop-preview-scanner-support",
    "junit:junit",
    "io.github.sergio-sastre.ComposablePreviewScanner:android",
  )
  requiredLibraries.forEach { dependencies.checkExists(it) }
}
