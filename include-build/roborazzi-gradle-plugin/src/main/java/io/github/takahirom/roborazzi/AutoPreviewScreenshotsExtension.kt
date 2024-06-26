package io.github.takahirom.roborazzi

import com.android.build.api.variant.Variant
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject


open class AutoPreviewScreenshotsExtension @Inject constructor(objects: ObjectFactory) {
  val enabled: Property<Boolean> = objects.property(Boolean::class.java)
  val scanPackages: ListProperty<String> = objects.listProperty(String::class.java)
}

fun setupGeneratedScreenshotTest(
  project: Project,
  variant: Variant,
  extension: AutoPreviewScreenshotsExtension
) {
  val scanPackages = extension.scanPackages.get()
  assert(scanPackages.isNotEmpty())
  addPreviewScreenshotLibraries(variant, project)
  val generateTestsTask = project.tasks.register(
    "generate${variant.name.capitalize()}PreviewScreenshotTests",
    GeneratePreviewScreenshotTestsTask::class.java
  ) {
    it.outputDir.set(project.layout.buildDirectory.dir("generated/roborazzi/preview-screenshot"))
    it.scanPackages.set(scanPackages)
  }
  variant.unitTest?.sources?.java?.addGeneratedSourceDirectory(
    generateTestsTask,
    GeneratePreviewScreenshotTestsTask::outputDir
  )
}

private fun addPreviewScreenshotLibraries(
  variant: Variant,
  project: Project
) {
  val configurationName = "test${variant.name.capitalize()}Implementation"

  val roborazziVersion = BuildConfig.libraryVersionsMap["roborazzi"]
  project.dependencies.add(
    configurationName,
    "io.github.takahirom.roborazzi:roborazzi-compose:$roborazziVersion"
  )
  project.dependencies.add(configurationName, "io.github.takahirom.roborazzi:roborazzi:$roborazziVersion")
  project.dependencies.add(configurationName, "junit:junit:${BuildConfig.libraryVersionsMap["junit"]}")
  project.dependencies.add(configurationName, "org.robolectric:robolectric:${BuildConfig.libraryVersionsMap["robolectric"]}")

  project.repositories.add(project.repositories.maven { it.setUrl("https://jitpack.io") })
  project.repositories.add(project.repositories.mavenCentral())
  project.repositories.add(project.repositories.google())
  project.dependencies.add(
    configurationName,
    "com.github.sergio-sastre.ComposablePreviewScanner:android:${BuildConfig.libraryVersionsMap["composable-preview-scanner"]}"
  )
}

abstract class GeneratePreviewScreenshotTestsTask : DefaultTask() {
  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @get:Input
  var scanPackages: ListProperty<String> = project.objects.listProperty(String::class.java)

  @TaskAction
  fun generateTests() {
    val testDir = outputDir.get().asFile
    testDir.mkdirs()

    val packagesExpr = scanPackages.get().joinToString(", ") { "\"$it\"" }
    val scanPackageTreeExpr = ".scanPackageTrees($packagesExpr)"

    File(testDir, "GeneratedPreviewScreenshotTest.kt").writeText(
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
            import com.github.takahirom.roborazzi.DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH


            @RunWith(ParameterizedRobolectricTestRunner::class)
            @GraphicsMode(GraphicsMode.Mode.NATIVE)
            class PreviewParameterizedTests(
                private val preview: ComposablePreview<AndroidPreviewInfo>,
            ) {

                companion object {
                    @JvmStatic
                    @ParameterizedRobolectricTestRunner.Parameters
                    fun values(): List<ComposablePreview<AndroidPreviewInfo>> =
                        AndroidComposablePreviewScanner()
                            $scanPackageTreeExpr
                            .getPreviews()
                }
                
                @GraphicsMode(GraphicsMode.Mode.NATIVE)
                @Config(sdk = [30])
                @Test
                fun snapshot() {
                    val filePath =
                        DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH + "/" + preview.methodName + ".png"
                    captureRoboImage(filePath = filePath) {
                        preview()
                    }
                }

            }
        """.trimIndent()
    )
  }
}