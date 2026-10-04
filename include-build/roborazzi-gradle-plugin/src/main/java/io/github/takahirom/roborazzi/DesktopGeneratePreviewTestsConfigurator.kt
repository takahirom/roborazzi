package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.AnnotationFilter
import com.github.takahirom.roborazzi.DesktopPreviewDeviceProfile
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.ROBORAZZI_DESKTOP_DEVICE_PROFILE_PROPERTY
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.CompilationExecutionSource
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTestRun
import java.net.URLEncoder
import java.util.Locale

/**
 * Functions for generating Compose preview tests for the Compose Desktop (JVM) target.
 * Unlike the Robolectric generator this hooks Kotlin JVM targets, so it must not
 * reference any AGP classes.
 */
@OptIn(ExperimentalRoborazziApi::class)
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
    setupDeviceProfiles(
      project = project,
      roborazziExtension = roborazziExtension,
      target = target,
      testCompilation = testCompilation,
    )
  }
}

/**
 * Gives every test run of the target the device profile configured for it.
 *
 * A test run is the unit a profile applies to: the Roborazzi plugin already turns each one into its
 * own set of tasks and its own output directory, so capturing the same previews under two profiles
 * only needs the test JVM to be told which one it is running.
 */
@OptIn(ExperimentalRoborazziApi::class)
private fun setupDeviceProfiles(
  project: Project,
  roborazziExtension: RoborazziExtension,
  target: KotlinJvmTarget,
  testCompilation: KotlinJvmCompilation,
) {
  val extension = roborazziExtension.generateComposePreviewDesktopTests
  val profileByTestRun = extension.deviceProfileByTestRun.getOrElse(emptyMap())
  val unknownTestRuns = profileByTestRun.keys - target.testRuns.names
  check(unknownTestRuns.isEmpty()) {
    "Roborazzi: generateComposePreviewDesktopTests.deviceProfileByTestRun names the test " +
      "run(s) $unknownTestRuns, but the JVM target '${target.name}' has ${target.testRuns.names}. " +
      "Create the test run first, e.g. kotlin { jvm(\"${target.name}\") { " +
      "testRuns.create(\"${unknownTestRuns.firstOrNull()}\") } }."
  }
  // Two runs render the same previews under different profiles, so without separate output
  // directories the second recording would overwrite the first baseline instead of adding to it.
  check(profileByTestRun.isEmpty() || roborazziExtension.separateOutputDirs.get()) {
    "Roborazzi: generateComposePreviewDesktopTests.deviceProfileByTestRun needs " +
      "roborazzi.separateOutputDirs = true, otherwise every test run of the JVM target " +
      "'${target.name}' records into the same directory and the profiles overwrite each other."
  }

  target.testRuns.all { testRun ->
    val profile = if (testRun.name == DEFAULT_TEST_RUN_NAME) {
      profileByTestRun[testRun.name] ?: extension.deviceProfile.orNull
    } else {
      profileByTestRun[testRun.name]
    }
    // A run pointed somewhere else never executes a generated preview test, so it has nothing to
    // render and no reason to be asked for a profile.
    if (profile == null && !testRun.runs(testCompilation)) {
      return@all
    }
    // No default. The profile decides the size and density of every golden the run records, and no
    // value is right for every project, so an unconfigured run is a question for the build rather
    // than something to guess at.
    checkNotNull(profile) { missingDeviceProfileMessage(target.name, testRun.name) }
    testRun.executionTask.configure { test ->
      applyDeviceProfile(test, profile)
    }
  }
}

/**
 * Whether this test run executes [compilation], which is where the generated preview tests live.
 *
 * A project can point a test run at another compilation with `setExecutionSourceFrom`, and the
 * generated tests are then not part of that run at all. Anything this cannot read as a single
 * compilation counts as running it - an execution source is set lazily and may not be there yet,
 * and a run can be given several compilations - so the runs the missing-profile check exists for
 * keep failing the build as before.
 */
private fun KotlinJvmTestRun.runs(compilation: KotlinJvmCompilation): Boolean {
  val source = runCatching { executionSource }.getOrNull() ?: return true
  if (source !is CompilationExecutionSource<*>) return true
  return source.compilation == compilation
}

/**
 * Tells a build that configured desktop preview tests but no profile how to pick one.
 *
 * The presets it offers come from [DesktopPreviewDeviceProfile.PRESET_CHOICES] so that adding a
 * preset and forgetting to mention it here is not possible.
 */
@OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
private fun missingDeviceProfileMessage(targetName: String, testRunName: String?): String {
  val setter = if (testRunName == null || testRunName == DEFAULT_TEST_RUN_NAME) {
    "deviceProfile = <preset>"
  } else {
    "deviceProfileByTestRun.put(\"$testRunName\", <preset>)"
  }
  val where =
    if (testRunName == null) "this project"
    else "the JVM target '$targetName' test run '$testRunName'"
  return """
    |Roborazzi: generateComposePreviewDesktopTests has no device profile for $where.
    |
    |The profile decides the surface size and the density, which is to say what every golden this
    |module records looks like. There is no default, because no value is right for every project.
    |Pick one:
    |
    |${DesktopPreviewDeviceProfile.PRESET_CHOICES}
    |
    |and set it with:
    |
    |  roborazzi {
    |    generateComposePreviewDesktopTests {
    |      $setter
    |    }
    |  }
  """.trimMargin()
}


@OptIn(ExperimentalRoborazziApi::class)
private fun applyDeviceProfile(test: Test, profile: DesktopPreviewDeviceProfile?) {
  val encoded = profile?.encode()
  // Declared as a task input so switching a run's profile re-renders instead of reporting
  // UP-TO-DATE with the previous profile's images still in place.
  test.inputs.property("roborazziDesktopDeviceProfile", encoded).optional(true)
  if (encoded == null) return
  // A jvmArgumentProvider rather than test.systemProperty: the plugin's own doFirst copies every
  // -Proborazzi.* gradle property into systemProperties, and whichever doFirst ran last would win.
  test.jvmArgumentProviders.add(DeviceProfileArgumentProvider(encoded))
}

/**
 * Named class rather than a lambda so the configuration cache can serialize it.
 *
 * The encoded profile is declared as a task input by [applyDeviceProfile], not here, to keep the
 * input name stable if this provider ever carries more than one argument.
 */
@OptIn(ExperimentalRoborazziApi::class)
internal class DeviceProfileArgumentProvider(
  private val encodedProfile: String,
) : CommandLineArgumentProvider {
  override fun asArguments(): Iterable<String> =
    listOf("-D$ROBORAZZI_DESKTOP_DEVICE_PROFILE_PROPERTY=$encodedProfile")
}

/** The name Kotlin gives a JVM target's test run when the build does not create extra ones. */
private const val DEFAULT_TEST_RUN_NAME = "test"

@OptIn(ExperimentalRoborazziApi::class)
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
    // A kotlin-jvm project has exactly one test task, so there is no run to key a profile by.
    check(extension.deviceProfileByTestRun.getOrElse(emptyMap()).isEmpty()) {
      "Roborazzi: generateComposePreviewDesktopTests.deviceProfileByTestRun only applies to " +
        "Kotlin Multiplatform projects, where a JVM target can have several test runs. This " +
        "project applies the Kotlin JVM plugin and has a single 'test' task, so use " +
        "generateComposePreviewDesktopTests.deviceProfile instead."
    }
    val profile = extension.deviceProfile.orNull
    checkNotNull(profile) { missingDeviceProfileMessage(targetName = "jvm", testRunName = null) }
    project.tasks.named("test", Test::class.java).configure { test ->
      applyDeviceProfile(test, profile)
    }
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
  // project.provider carries no task-dependency information (unlike mapping
  // testTaskProvider, which would create a generate -> test dependency cycle) and
  // is only evaluated at execution time, so late maxParallelForks configuration is
  // still picked up.
  val generatedTestClassCountProvider = project.provider {
    extension.generatedTestClassCount.orNull ?: testTaskProvider.get().maxParallelForks
  }

  validateCustomTesterConfiguration(extension)
  failWhenMixedWithRobolectricPreviewTests(roborazziExtension)
  verifyDesktopLibraryDependencies(project)

  val generateTestsTask = project.tasks.register(
    "generate${variantName.capitalize(Locale.ROOT)}ComposePreviewDesktopTests",
    GenerateComposePreviewDesktopTestsTask::class.java
  ) {
    it.outputDir.set(project.layout.buildDirectory.dir("generated/roborazzi/preview-screenshot/$variantName"))
    it.scanPackageTrees.set(extension.packages)
    it.includePrivatePreviews.set(extension.includePrivatePreviews)
    it.testerQualifiedClassName.set(extension.testerQualifiedClassName)
    it.generatedTestClassCount.set(generatedTestClassCountProvider)
    it.annotationFilter.set(extension.annotationFilter.orElse(AnnotationFilter.Filter.RoboPreviewExclude))
  }
  // Registering the provider as a source directory carries the task dependency,
  // so the generate task runs before the test compilation.
  testSourceSet.kotlin.srcDir(generateTestsTask.flatMap { it.outputDir })
  testTaskProvider.configure {
    it.inputs.dir(generateTestsTask.flatMap { it.outputDir })
      // Name the property and normalize by relative path so the checkout location
      // does not become part of the build cache key (relocatability).
      // https://github.com/takahirom/roborazzi/issues/918
      .withPropertyName("roborazziGeneratedPreviewTests")
      .withPathSensitivity(PathSensitivity.RELATIVE)
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
private fun failWhenMixedWithRobolectricPreviewTests(
  roborazziExtension: RoborazziExtension,
) {
  val robolectricEnabled =
    roborazziExtension.generateComposePreviewRobolectricTests.enable.orNull == true
  val separateOutputDirs = roborazziExtension.separateOutputDirs.get()
  if (robolectricEnabled && !separateOutputDirs) {
    // An error rather than a warning: the two generators use the same screenshot
    // names, so one recording run would silently overwrite the other baseline.
    error(
      "Roborazzi: Both generateComposePreviewRobolectricTests and generateComposePreviewDesktopTests " +
        "are enabled in this module, and they use the same screenshot names, so the Android and " +
        "desktop screenshots would overwrite each other in the shared output directory. " +
        "Please set 'roborazzi.separateOutputDirs = true' so each task gets its own subdirectory, " +
        "or disable one of the generators."
    )
  }
}

private fun verifyDesktopLibraryDependencies(project: Project) {
  val declaredDependencies = project.configurations.flatMap { it.dependencies }

  fun hasDependency(libraryName: String): Boolean {
    val (group, name) = libraryName.split(":")
    return declaredDependencies.any { dependency ->
      dependency.name == name &&
        // A project dependency reports its target's group, which is only set once that project has
        // been evaluated. In a composite build it often has not been, and the group then reads as
        // the including build's default, so matching a project dependency on its name alone is the
        // only reliable check.
        (dependency.group == group || dependency is ProjectDependency)
    }
  }

  fun checkExists(libraryName: String) {
    if (!hasDependency(libraryName)) {
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

  // junit:junit is deliberately not checked here: it commonly arrives transitively
  // (e.g. via kotlin("test")), and this check only sees direct declarations.
  val requiredLibraries = listOf(
    "io.github.takahirom.roborazzi:roborazzi-compose-desktop-preview-scanner-support",
    "io.github.sergio-sastre.ComposablePreviewScanner:android",
  )
  requiredLibraries.forEach { checkExists(it) }
}
