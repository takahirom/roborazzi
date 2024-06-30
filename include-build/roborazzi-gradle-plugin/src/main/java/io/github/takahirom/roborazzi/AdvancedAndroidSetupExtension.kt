package io.github.takahirom.roborazzi

import com.android.build.api.variant.Variant
import com.android.build.gradle.TestedExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.testing.Test
import java.io.File
import javax.inject.Inject

open class AdvancedAndroidSetupExtension @Inject constructor(objects: ObjectFactory) {
  /**
   * Default value is false.
   */
  val enable: Property<Boolean> = objects.property(Boolean::class.java)
    .convention(false)
  val generatePreviewTests: GeneratePreviewTestsExtension =
    objects.newInstance(GeneratePreviewTestsExtension::class.java)

  fun generatePreviewTests(action: GeneratePreviewTestsExtension.() -> Unit) {
    generatePreviewTests.action()
  }

  val libraryDependencies: LibraryDependenciesExtension =
    objects.newInstance(LibraryDependenciesExtension::class.java)

  fun libraryDependencies(action: LibraryDependenciesExtension.() -> Unit) {
    libraryDependencies.action()
  }

  val testConfig: TestConfigExtension = objects.newInstance(TestConfigExtension::class.java)

  fun testConfig(action: TestConfigExtension.() -> Unit) {
    testConfig.action()
  }
}

open class GeneratePreviewTestsExtension @Inject constructor(objects: ObjectFactory) {
  val enable: Property<Boolean> = objects.property(Boolean::class.java)
  val scanPackages: ListProperty<String> = objects.listProperty(String::class.java)
}

open class LibraryDependenciesExtension @Inject constructor(objects: ObjectFactory) {
  val enable: Property<Boolean> = objects.property(Boolean::class.java)
  val roborazziVersion: Property<String> = objects.property(String::class.java)
  val junitVersion: Property<String> = objects.property(String::class.java)
  val robolectricVersion: Property<String> = objects.property(String::class.java)
  val composablePreviewScannerVersion: Property<String> = objects.property(String::class.java)

  companion object {
    const val SKIP = "SKIP_DEPENDENCY"
  }
}

open class TestConfigExtension @Inject constructor(objects: ObjectFactory) {
  val enable: Property<Boolean> = objects.property(Boolean::class.java)
  val includeAndroidResources: Property<Boolean> = objects.property(Boolean::class.java)
  val roborazziFilePathStrategy: Property<String> = objects.property(String::class.java)
  val robolectricRenderMode: Property<String> = objects.property(String::class.java)
}

fun setupAndroidSetupExtension(
  project: Project,
  variant: Variant,
  extension: AdvancedAndroidSetupExtension,
  androidExtension: TestedExtension,
  testTaskProvider: TaskCollection<Test>
) {
  // Prioritize the enable property in the extension over the global enable property.
  if ((extension.generatePreviewTests.enable.orNull ?: extension.enable.orNull) == true) {
    setupGeneratePreviewTestsTask(project, variant, extension.generatePreviewTests)
  }
  if ((extension.libraryDependencies.enable.orNull ?: extension.enable.orNull) == true) {
    addLibraryDependencies(variant, project, extension.libraryDependencies)
  }
  if ((extension.testConfig.enable.orNull ?: extension.enable.orNull) == true) {
    setupTestConfig(testTaskProvider, extension.testConfig, androidExtension)
  }
}

private fun setupGeneratePreviewTestsTask(
  project: Project,
  variant: Variant,
  extension: GeneratePreviewTestsExtension
) {
  assert(extension.scanPackages.get().orEmpty().isNotEmpty()) {
    "Please set androidSetup.generatePreviewTests.scanPackages in the generatePreviewTests extension or set androidSetup.generatePreviewTests.enable = false." +
      "See https://github.com/sergio-sastre/ComposablePreviewScanner?tab=readme-ov-file#how-to-use for more information."
  }

  val generateTestsTask = project.tasks.register(
    "generate${variant.name.capitalize()}PreviewScreenshotTests",
    GeneratePreviewScreenshotTestsTask::class.java
  ) {
    // It seems that this directory path is overridden by addGeneratedSourceDirectory.
    // The generated tests will be located in build/JAVA/generate[VariantName]PreviewScreenshotTests.
    it.outputDir.set(project.layout.buildDirectory.dir("generated/roborazzi/preview-screenshot"))
    it.scanPackageTrees.set(extension?.scanPackages)
  }
  // We need to use Java here; otherwise, the generate task will not be executed.
  // https://stackoverflow.com/a/76870110/4339442
  variant.unitTest?.sources?.java?.addGeneratedSourceDirectory(
    generateTestsTask,
    GeneratePreviewScreenshotTestsTask::outputDir
  )
}

private fun setupTestConfig(
  testTaskProvider: TaskCollection<Test>,
  testConfiguration: TestConfigExtension,
  androidExtension: TestedExtension
) {
  // Default true
  if (testConfiguration.includeAndroidResources.orNull != false) {
    androidExtension.testOptions.unitTests.isIncludeAndroidResources = true
  }
  testTaskProvider.configureEach { testTask: Test ->
    // see: https://github.com/takahirom/roborazzi?tab=readme-ov-file#roborazzirecordfilepathstrategy
    testTask.systemProperties["roborazzi.record.filePathStrategy"] =
      testConfiguration.roborazziFilePathStrategy.orNull
        ?: "relativePathFromRoborazziContextOutputDirectory"
    // see: https://github.com/takahirom/roborazzi?tab=readme-ov-file#robolectricpixelcopyrendermode
    testTask.systemProperties["robolectric.pixelCopyRenderMode"] =
      testConfiguration.robolectricRenderMode.orNull
        ?: "hardware"
  }
}

private fun addLibraryDependencies(
  variant: Variant,
  project: Project,
  libraryVersionsExtension: LibraryDependenciesExtension?
) {
  val configurationName = "test${variant.name.capitalize()}Implementation"

  fun Property<String>?.getLibraryVersion(libraryName: String): String {
    if (this == null) {
      return BuildConfig.defaultLibraryVersionsMap[libraryName]!!
    }
    if (this.orNull == LibraryDependenciesExtension.SKIP) {
      return LibraryDependenciesExtension.SKIP
    }
    return convention(BuildConfig.defaultLibraryVersionsMap[libraryName]!!).get()
  }

  val roborazziVersion = libraryVersionsExtension?.roborazziVersion.getLibraryVersion("roborazzi")
  fun DependencyHandler.addIfNotSkip(libraryName: String, version: String) {
    if (version != LibraryDependenciesExtension.SKIP) {
      add(configurationName, "$libraryName:$version")
    }
  }
  project.dependencies.addIfNotSkip(
    libraryName = "io.github.takahirom.roborazzi:roborazzi-compose",
    version = roborazziVersion
  )
  project.dependencies.addIfNotSkip(
    libraryName = "io.github.takahirom.roborazzi:roborazzi",
    version = roborazziVersion
  )
  val junitLibraryVersion = libraryVersionsExtension?.junitVersion.getLibraryVersion("junit")
  project.dependencies.addIfNotSkip(
    "junit:junit", junitLibraryVersion
  )
  val robolectricLibraryVersion =
    libraryVersionsExtension?.robolectricVersion.getLibraryVersion("robolectric")
  project.dependencies.addIfNotSkip(
    libraryName = "org.robolectric:robolectric",
    version = robolectricLibraryVersion
  )

  // For ComposablePreviewScanner
  val composePreviewScannerLibraryVersion =
    libraryVersionsExtension?.composablePreviewScannerVersion.getLibraryVersion("composable-preview-scanner")
  if (composePreviewScannerLibraryVersion != LibraryDependenciesExtension.SKIP) {
    project.repositories.add(project.repositories.maven { it.setUrl("https://jitpack.io") })
    project.repositories.add(project.repositories.mavenCentral())
    project.repositories.add(project.repositories.google())
    project.dependencies.addIfNotSkip(
      libraryName = "com.github.sergio-sastre.ComposablePreviewScanner:android",
      version = composePreviewScannerLibraryVersion
    )
  }
}

abstract class GeneratePreviewScreenshotTestsTask : DefaultTask() {
  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @get:Input
  var scanPackageTrees: ListProperty<String> = project.objects.listProperty(String::class.java)

  @TaskAction
  fun generateTests() {
    val testDir = outputDir.get().asFile
    testDir.mkdirs()

    val packagesExpr = scanPackageTrees.get().joinToString(", ") { "\"$it\"" }
    val scanPackageTreeExpr = ".scanPackageTrees($packagesExpr)"

    File(testDir, "PreviewParameterizedTests.kt").writeText(
      """
            import org.junit.Test
            import org.junit.runner.RunWith
            import org.robolectric.ParameterizedRobolectricTestRunner
            import org.robolectric.annotation.Config
            import org.robolectric.annotation.GraphicsMode
            import com.github.takahirom.roborazzi.captureRoboImage
            import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview
            import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
            import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
            import sergio.sastre.composable.preview.scanner.android.screenshotid.AndroidPreviewScreenshotIdBuilder
            import com.github.takahirom.roborazzi.DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH


            @RunWith(ParameterizedRobolectricTestRunner::class)
            @GraphicsMode(GraphicsMode.Mode.NATIVE)
            class PreviewParameterizedTests(
                private val preview: ComposablePreview<AndroidPreviewInfo>,
            ) {

                companion object {
                    val previews: List<ComposablePreview<AndroidPreviewInfo>> by lazy {
                        AndroidComposablePreviewScanner()
                            $scanPackageTreeExpr
                            .getPreviews()
                    }
                    @JvmStatic
                    @ParameterizedRobolectricTestRunner.Parameters
                    fun values(): List<ComposablePreview<AndroidPreviewInfo>> =
                        previews
                }
                
                fun createScreenshotIdFor(preview: ComposablePreview<AndroidPreviewInfo>) = 
                  AndroidPreviewScreenshotIdBuilder(preview)
                         .ignoreClassName()
                         .build()
                
                @GraphicsMode(GraphicsMode.Mode.NATIVE)
                @Config(sdk = [30])
                @Test
                fun test() {
                    val filePath = createScreenshotIdFor(preview) + ".png"
                    captureRoboImage(filePath = filePath) {
                        preview()
                    }
                }

            }
        """.trimIndent()
    )
  }
}