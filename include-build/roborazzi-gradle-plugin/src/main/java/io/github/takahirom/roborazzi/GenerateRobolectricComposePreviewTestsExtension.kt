package io.github.takahirom.roborazzi

import com.android.build.api.variant.Variant
import com.android.build.gradle.TestedExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
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
import org.gradle.invocation.DefaultGradle
import java.io.File
import java.net.URLEncoder
import javax.inject.Inject

open class GenerateComposePreviewRobolectricTestsExtension @Inject constructor(objects: ObjectFactory) {
  val enable: Property<Boolean> = objects.property(Boolean::class.java)
    .convention(false)

  /**
   * The package names to scan for the Composable Previews.
   */
  val packages: ListProperty<String> = objects.listProperty(String::class.java)

  /**
   * The fully qualified class name of the custom test class that implements [com.github.takahirom.roborazzi.RobolectricPreviewTest].
   */
  val customTestQualifiedClassName: Property<String> = objects.property(String::class.java)
    .convention("com.github.takahirom.roborazzi.DefaultRobolectricPreviewTest")

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
}

fun generateRobolectricPreviewTestsIfNeeded(
  project: Project,
  variant: Variant,
  extension: GenerateComposePreviewRobolectricTestsExtension,
  androidExtension: TestedExtension,
  testTaskProvider: TaskCollection<Test>
) {
  if ((extension.enable.orNull) != true) {
    return
  }
  val logger = project.logger
  setupGeneratePreviewTestsTask(
    project = project,
    variant = variant,
    scanPackages = extension.packages,
    customTestQualifiedClassName = extension.customTestQualifiedClassName,
    robolectricConfig = extension.robolectricConfig
  )
  project.afterEvaluate {
    // We use afterEvaluate only for verify
    check(variant.unitTest != null) {
      "Roborazzi: Please enable unit tests for the variant '${variant.name}' in the 'build.gradle' file."
    }
    verifyMavenRepository(project)
    verifyLibraryDependencies(project)
    verifyTestConfig(testTaskProvider, androidExtension, logger)
  }
}

private fun setupGeneratePreviewTestsTask(
  project: Project,
  variant: Variant,
  scanPackages: ListProperty<String>,
  customTestQualifiedClassName: Property<String>,
  robolectricConfig: MapProperty<String, String>,
) {
  check(scanPackages.get().orEmpty().isNotEmpty()) {
    "Please set roborazzi.generateRobolectricPreviewTests.packages in the generatePreviewTests extension or set roborazzi.generateRobolectricPreviewTests.enable = false." +
      "See https://github.com/sergio-sastre/ComposablePreviewScanner?tab=readme-ov-file#how-to-use for more information."
  }

  val generateTestsTask = project.tasks.register(
    "generate${variant.name.capitalize()}PreviewScreenshotTests",
    GeneratePreviewScreenshotTestsTask::class.java
  ) {
    // It seems that this directory path is overridden by addGeneratedSourceDirectory.
    // The generated tests will be located in build/JAVA/generate[VariantName]PreviewScreenshotTests.
    it.outputDir.set(project.layout.buildDirectory.dir("generated/roborazzi/preview-screenshot"))
    it.scanPackageTrees.set(scanPackages)
    it.customTestQualifiedClassName.set(customTestQualifiedClassName)
    it.robolectricConfig.set(robolectricConfig)
  }
  // We need to use sources.java here; otherwise, the generate task will not be executed.
  // https://stackoverflow.com/a/76870110/4339442
  variant.unitTest?.sources?.java?.addGeneratedSourceDirectory(
    generateTestsTask,
    GeneratePreviewScreenshotTestsTask::outputDir
  )
}

private fun verifyMavenRepository(project: Project) {
  // Check if the jitpack repository is added.
  val hasJitpackRepo = project.repositories.any {
    it is MavenArtifactRepository &&
      it.url.toString().contains("https://jitpack.io")
  }
  // settingsEvaluated{} is not called
  // when it is already evaluated so we need to call `settingsEvaluated`
  // before the project is evaluated.
  // So we need to use the following workaround.
  // https://github.com/gradle/gradle/issues/27741
  val setting = (project.gradle as? DefaultGradle)?.getSettings() ?: return
  val hasJitpackRepoInSettings = setting.pluginManagement.repositories.any {
    it is MavenArtifactRepository && it.url.toString().contains("https://jitpack.io")
  } || setting.dependencyResolutionManagement.repositories.any {
    it is MavenArtifactRepository && it.url.toString().contains("https://jitpack.io")
  }
  if (!hasJitpackRepo && !hasJitpackRepoInSettings) {
    error(
      "Roborazzi: Please add the following 'maven' repository to the 'repositories' block in the 'build.gradle' file.\n" +
        "build.gradle: \nrepositories {\nmaven { url 'https://jitpack.io' } \n}\n" +
        "build.gradle.kts: \nrepositories {\nmaven { url = uri(\"https://jitpack.io\") } \n}\n" +
        "This is necessary to download the ComposablePreviewScanner."
    )
  }
}

private fun verifyTestConfig(
  testTaskProvider: TaskCollection<Test>,
  androidExtension: TestedExtension,
  logger: Logger
) {
  if (!androidExtension.testOptions.unitTests.isIncludeAndroidResources) {
    logger.warn(
      "Roborazzi: Please set 'android.testOptions.unitTests.isIncludeAndroidResources = true' in the 'build.gradle' file. " +
        "This is advisable to avoid issues with ActivityNotFoundException."
    )
  }
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
  val configurations = listOf(
    // Android
    "testImplementation",
    // KMP Android
    "androidUnitTestImplementation"
  )
  fun dependencies(configurations: List<String>): List<Pair<String, List<Pair<String?, String>>>> {
    return configurations.map { configuration ->
      try {
        configuration to project.configurations
          .getByName(configuration).dependencies
          .map { dependency -> dependency.group to dependency.name }
      } catch (e: org.gradle.api.artifacts.UnknownConfigurationException) {
        configuration to emptyList()
      }
    }
  }
  verifyLibraryDependencies(dependencies(configurations))
}

private fun verifyLibraryDependencies(
  configurationToDependencies: List<Pair<String, List<Pair<String?, String>>>>
) {
  val allDependencies = configurationToDependencies.flatMap { it.second }
  val allConfigurationNames = configurationToDependencies.map { it.first }
  fun checkExists(libraryName: String) {
    if (!allDependencies.contains(libraryName.split(":").let { it[0] to it[1] })) {
      val configurationNames = allConfigurationNames.joinToString(" or ") { "'$it'" }
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
    "com.github.sergio-sastre.ComposablePreviewScanner:android",
  )
  requiredLibraries.forEach { checkExists(it) }
}

abstract class GeneratePreviewScreenshotTestsTask : DefaultTask() {
  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @get:Input
  var scanPackageTrees: ListProperty<String> = project.objects.listProperty(String::class.java)

  @get:Input
  abstract val customTestQualifiedClassName: Property<String>

  @get:Input
  abstract val robolectricConfig: MapProperty<String, String>

  @TaskAction
  fun generateTests() {
    val testDir = outputDir.get().asFile
    testDir.mkdirs()

    val packagesExpr = scanPackageTrees.get().joinToString(", ") { "\"$it\"" }

    val generatedClassFQDN = "com.github.takahirom.roborazzi.RoborazziPreviewParameterizedTests"
    val packageName = generatedClassFQDN.substringBeforeLast(".")
    val className = generatedClassFQDN.substringAfterLast(".")
    val directory = File(testDir, packageName.replace(".", "/"))
    directory.mkdirs()
    val robolectricConfigString =
      "@Config(" + robolectricConfig.get().entries.joinToString(", ") { (key, value) ->
        "$key = $value"
      } + ")"
    val customTestQualifiedClassNameString = customTestQualifiedClassName.get()
    File(directory, "$className.kt").writeText(
      """
            package $packageName
            import org.junit.Test
            import org.junit.runner.RunWith
            import org.robolectric.ParameterizedRobolectricTestRunner
            import org.robolectric.annotation.Config
            import org.robolectric.annotation.GraphicsMode
            import com.github.takahirom.roborazzi.*
            import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview
            import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
            import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
            import sergio.sastre.composable.preview.scanner.android.screenshotid.AndroidPreviewScreenshotIdBuilder


            @RunWith(ParameterizedRobolectricTestRunner::class)
            @GraphicsMode(GraphicsMode.Mode.NATIVE)
            class $className(
                private val preview: ComposablePreview<AndroidPreviewInfo>,
            ) {

                companion object {
                    // lazy for performance
                    val previews: List<ComposablePreview<AndroidPreviewInfo>> by lazy {
                        getRobolectricPreviewTest("$customTestQualifiedClassNameString").previews(
                            $packagesExpr
                        )
                    }
                    @JvmStatic
                    @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
                    fun values(): List<ComposablePreview<AndroidPreviewInfo>> =
                        previews
                }
                
                
                @GraphicsMode(GraphicsMode.Mode.NATIVE)
                $robolectricConfigString
                @Test
                fun test() {
                    getRobolectricPreviewTest("$customTestQualifiedClassNameString").test(preview)
                }

            }
        """.trimIndent()
    )
  }
}