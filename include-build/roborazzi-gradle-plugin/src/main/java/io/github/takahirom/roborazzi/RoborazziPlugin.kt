package io.github.takahirom.roborazzi

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.gradle.TestedExtension
import com.github.takahirom.roborazzi.CaptureResult
import com.github.takahirom.roborazzi.CaptureResults
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziReportConst
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.ExecutionTaskHolder
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithTests
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.targets.native.KotlinNativeBinaryTestRun
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale
import javax.inject.Inject
import kotlin.reflect.KClass

private const val DEFAULT_OUTPUT_DIR = "outputs/roborazzi"
private const val DEFAULT_TEMP_DIR = "intermediates/roborazzi"

open class RoborazziExtension @Inject constructor(objects: ObjectFactory) {
  val outputDir: DirectoryProperty = objects.directoryProperty()

  @ExperimentalRoborazziApi
  val compare: RoborazziCompareExtension =
    objects.newInstance(RoborazziCompareExtension::class.java)

  @ExperimentalRoborazziApi
  fun compare(action: Action<RoborazziCompareExtension>) {
    action.execute(compare)
  }

  @ExperimentalRoborazziApi
  val generateComposePreviewRobolectricTests: GenerateComposePreviewRobolectricTestsExtension =
    objects.newInstance(GenerateComposePreviewRobolectricTestsExtension::class.java)

  @ExperimentalRoborazziApi
  fun generateComposePreviewRobolectricTests(action: GenerateComposePreviewRobolectricTestsExtension.() -> Unit) {
    action(generateComposePreviewRobolectricTests)
  }
}

open class RoborazziCompareExtension @Inject constructor(objects: ObjectFactory) {
  val outputDir: DirectoryProperty = objects.directoryProperty()
}

val KnownImageFileExtensions: Set<String> = setOf("png", "gif", "jpg", "jpeg", "webp")

@Suppress("unused")
// From Paparazzi: https://github.com/cashapp/paparazzi/blob/a76702744a7f380480f323ffda124e845f2733aa/paparazzi/paparazzi-gradle-plugin/src/main/java/app/cash/paparazzi/gradle/PaparazziPlugin.kt
abstract class RoborazziPlugin : Plugin<Project> {

  val AbstractTestTask.systemProperties: MutableMap<String, Any?>
    get() = when (this) {
      is Test -> systemProperties
      is KotlinNativeTest -> object : AbstractMutableMap<String, Any?>() {
        override fun put(key: String, value: Any?): Any? {
          environment("SIMCTL_CHILD_" + key, value.toString())
          return null
        }

        override val entries: MutableSet<MutableMap.MutableEntry<String, Any?>>
          get() = environment.mapValues { it.value as Any? }.toMutableMap().entries
      }

      else -> throw IllegalStateException("Unsupported test task type: $this")
    }

  @OptIn(InternalRoborazziApi::class, ExperimentalRoborazziApi::class)
  override fun apply(project: Project) {
    val extension = project.extensions.create("roborazzi", RoborazziExtension::class.java)

    val verifyVariants = project.tasks.register("verifyRoborazzi")
    val compareVariants = project.tasks.register("compareRoborazzi")
    val recordVariants = project.tasks.register("recordRoborazzi")
    val verifyAndRecordVariants = project.tasks.register("verifyAndRecordRoborazzi")

    // For fixing unexpected skip test
    val outputDir =
      extension.outputDir.convention(project.layout.buildDirectory.dir(DEFAULT_OUTPUT_DIR))
    val compareOutputDirProvider =
      extension.compare.outputDir.convention(project.layout.buildDirectory.dir(DEFAULT_OUTPUT_DIR))
    val testTaskOutputDir: DirectoryProperty = project.objects.directoryProperty()
    val intermediateDir =
      testTaskOutputDir.convention(project.layout.buildDirectory.dir(DEFAULT_TEMP_DIR))

    val restoreOutputDirRoborazziTaskProvider =
      project.tasks.register(
        "restoreOutputDirRoborazzi",
        RestoreOutputDirRoborazziTask::class.java
      ) { task ->

        task.inputDir.set(intermediateDir.map {
          if (!it.asFile.exists()) {
            it.asFile.mkdirs()
          }
          it
        })
        task.outputDir.set(outputDir)
        task.onlyIf {
          val outputDirFile = task.outputDir.asFile.get()
          val inputDirFile = task.inputDir.asFile.get()
          (outputDirFile.listFiles()?.isEmpty() ?: true)
            && (inputDirFile.listFiles()?.isNotEmpty() ?: false)
        }
      }

    fun isAnyTaskRun(
      isRecordRun: Property<Boolean>,
      isVerifyRun: Property<Boolean>,
      isVerifyAndRecordRun: Property<Boolean>,
      isCompareRun: Property<Boolean>
    ) = isRecordRun.get() || isVerifyRun.get() || isVerifyAndRecordRun.get() || isCompareRun.get()

    fun hasRoborazziTaskProperty(roborazziProperties: Map<String, Any?>): Boolean {
      return roborazziProperties["roborazzi.test.record"] == "true" || roborazziProperties["roborazzi.test.verify"] == "true" || roborazziProperties["roborazzi.test.compare"] == "true"
    }

    fun <T : AbstractTestTask> findTestTaskProvider(
      testTaskClass: KClass<T>,
      testTaskName: String
    ): TaskCollection<T> =
      project.tasks.withType(testTaskClass.java)
        .matching {
          it.name == testTaskName
        }

    fun configureRoborazziTasks(
      variantSlug: String,
      testTaskName: String,
      testTaskClass: KClass<out AbstractTestTask> = Test::class
    ) {
      val testTaskOutputDirForEachVariant: DirectoryProperty = project.objects.directoryProperty()
      val intermediateDirForEachVariant =
        testTaskOutputDirForEachVariant.convention(
          project.layout.buildDirectory.dir(
            DEFAULT_TEMP_DIR
          )
        )

      // e.g. clearRoborazziDebug
      project.tasks.register("clearRoborazzi$variantSlug") {
        it.doLast {
          val outputDirFile = outputDir.get().asFile
          if (outputDirFile.exists()) {
            outputDirFile.walkTopDown().forEach { file ->
              if (KnownImageFileExtensions.contains(file.extension)) {
                file.delete()
              }
            }
            outputDirFile.mkdirs()
          }
          val intermediateDirFile = intermediateDirForEachVariant.get().asFile
          if (intermediateDirFile.exists()) {
            intermediateDirFile.walkTopDown().forEach { file ->
              if (KnownImageFileExtensions.contains(file.extension)) {
                file.delete()
              }
            }
            intermediateDirFile.mkdirs()
          }
        }
      }

      //      val reportOutputDir = project.layout.buildDirectory.dir("reports/roborazzi")
      //      val snapshotOutputDir = project.layout.projectDirectory.dir("src/test/snapshots")

      val recordTaskProvider =
        project.tasks.register("recordRoborazzi$variantSlug", RoborazziTask::class.java) {
          it.group = VERIFICATION_GROUP
        }
      recordVariants.configure { it.dependsOn(recordTaskProvider) }

      val compareReportGenerateTaskProvider =
        project.tasks.register(
          "compareRoborazzi$variantSlug",
          RoborazziTask::class.java
        ) {
          it.group = VERIFICATION_GROUP
        }
      compareVariants.configure { it.dependsOn(compareReportGenerateTaskProvider) }

      val verifyTaskProvider =
        project.tasks.register("verifyRoborazzi$variantSlug", RoborazziTask::class.java) {
          it.group = VERIFICATION_GROUP
        }
      verifyVariants.configure { it.dependsOn(verifyTaskProvider) }

      val verifyAndRecordTaskProvider =
        project.tasks.register(
          "verifyAndRecordRoborazzi$variantSlug",
          RoborazziTask::class.java
        ) {
          it.group = VERIFICATION_GROUP
        }
      verifyAndRecordVariants.configure { it.dependsOn(verifyAndRecordTaskProvider) }

      val isRecordRun = project.objects.property(Boolean::class.java)
      val isVerifyRun = project.objects.property(Boolean::class.java)
      val isCompareRun = project.objects.property(Boolean::class.java)
      val isVerifyAndRecordRun = project.objects.property(Boolean::class.java)

      project.gradle.taskGraph.whenReady { graph ->
        isRecordRun.set(recordTaskProvider.map { graph.hasTask(it) })
        isVerifyRun.set(verifyTaskProvider.map { graph.hasTask(it) })
        isVerifyAndRecordRun.set(verifyAndRecordTaskProvider.map { graph.hasTask(it) })
        isCompareRun.set(compareReportGenerateTaskProvider.map { graph.hasTask(it) })
      }

      val testTaskProvider = findTestTaskProvider(testTaskClass, testTaskName)
      val roborazziProperties: Map<String, Any?> =
        project.providers.gradlePropertiesPrefixedBy("roborazzi").get()

      val doesRoborazziRunProvider = isRecordRun.flatMap { isRecordRunValue ->
        isVerifyRun.flatMap { isVerifyRunValue ->
          isVerifyAndRecordRun.flatMap { isVerifyAndRecordRunValue ->
            isCompareRun.map { compareRunValue ->
              isRecordRunValue || isVerifyRunValue || isVerifyAndRecordRunValue || compareRunValue
                || hasRoborazziTaskProperty(roborazziProperties)
            }
          }
        }
      }
      val isCompareOrVerifyRunProvider = isVerifyRun.flatMap { isVerifyTaskRun ->
        isCompareRun.map { isCompareTaskRun ->
          isVerifyTaskRun || isCompareTaskRun
        }
      }.map { taskUseImageInput ->
        taskUseImageInput ||
          roborazziProperties["roborazzi.test.verify"] == "true" ||
          roborazziProperties["roborazzi.test.compare"] == "true"
      }

      val projectAbsolutePathProvider = project.providers.provider {
        project.projectDir.absolutePath
      }
      val outputDirRelativePathFromProjectProvider = outputDir.map { project.relativePath(it) }

      val resultDirFileProperty =
        project.layout.buildDirectory.dir(RoborazziReportConst.resultDirPathFromBuildDir)
      val resultDirFileTree =
        resultDirFileProperty.map { it.asFileTree }
      val resultDirRelativePathFromProjectProvider =
        resultDirFileProperty.map { project.relativePath(it) }
      val resultSummaryFileProperty =
        project.layout.buildDirectory.file(RoborazziReportConst.resultsSummaryFilePathFromBuildDir)
      val reportFileProperty =
        project.layout.buildDirectory.file(RoborazziReportConst.reportFilePathFromBuildDir)

      // The difference between finalizedTask and afterSuite is that
      // finalizedTask is called even if the test is skipped.
      val finalizeTestRoborazziTask = project.tasks.register(
        /* name = */ "finalizeTestRoborazzi$variantSlug",
        /* configurationAction = */ object : Action<Task> {
          override fun execute(finalizeTestTask: Task) {
            finalizeTestTask.onlyIf {
              val doesRoborazziRun = doesRoborazziRunProvider.get()
              finalizeTestTask.infoln("Roborazzi: roborazziTestFinalizer.onlyIf doesRoborazziRun $doesRoborazziRun")
              doesRoborazziRun
            }
            finalizeTestTask.doLast {
              val startCopy = System.currentTimeMillis()
              intermediateDir.get().asFile.mkdirs()
              intermediateDir.get().asFile.copyRecursively(
                target = outputDir.get().asFile,
                overwrite = true
              )
              finalizeTestTask.infoln("Roborazzi: finalizeTestRoborazziTask Copy files from ${intermediateDir.get()} to ${outputDir.get()} end ${System.currentTimeMillis() - startCopy}ms")
            }
          }
        })
      testTaskProvider
        .configureEach { test: AbstractTestTask ->
          val resultsDir = resultDirFileProperty.get().asFile
          val imageInputProvider: Provider<FileCollection> =
            isCompareOrVerifyRunProvider.flatMap { isImageInputUsed ->
              if (!isImageInputUsed) {
                // Note: this is not files in outputDir,
                // but empty input when running in record mode.
                outputDir.map { it.files(/* this means empty files*/) }
              } else if (restoreOutputDirRoborazziTaskProvider.isPresent) {
                // Previous outputs are an input when running in compare or verify mode.
                // However, during record runs the output dir might not exist yet, so we use
                // files() to express that it is optional.
                // See also: https://github.com/gradle/gradle/issues/2016
                restoreOutputDirRoborazziTaskProvider.map {
                  if (!it.outputDir.get().asFile.exists()) {
                    it.outputDir.get().asFile.mkdirs()
                  }
                  test.infoln("Roborazzi: Set input dir ${it.outputDir.get()} to test task")
                  it.outputDir.files(".")
                }
              } else {
                outputDir.map {
                  if (!it.asFile.exists()) {
                    it.asFile.mkdirs()
                  }
                  test.infoln("Roborazzi: Set input dir $it to test task")
                  it.files(".")
                }
              }
            }
          test.inputs.files(
            imageInputProvider
          )
          test.outputs.dirs(
            compareOutputDirProvider.flatMap { compareOutputDir: Directory ->
              isCompareOrVerifyRunProvider.flatMap { isCompareOrVerifyRun ->
                imageInputProvider
                  .map { imageInput: FileCollection ->
                    if (!isCompareOrVerifyRun) {
                      // If it is not compare or verify, we don't need to output anything for comparison
                      outputDir.files(/* empty files */)
                    } else if (imageInput.files.any {
                        // Check if the compare output directory is the same as the input directory
                        if (it.isDirectory) {
                          it.absolutePath == compareOutputDir.asFile.absolutePath
                        } else {
                          it.parentFile.absolutePath == compareOutputDir.asFile.absolutePath
                        }
                      }) {
                      outputDir.files(/* empty files */)
                    } else {
                      if (!compareOutputDir.asFile.exists()) {
                        compareOutputDir.asFile.mkdirs()
                      }
                      compareOutputDir
                    }
                  }
              }
            }.map {
              test.infoln("Roborazzi: Set output dir ${it} to test task")
              it
            })
          test.outputs.dir(intermediateDirForEachVariant.map {
            test.infoln("Roborazzi: Set output dir $it to test task")
            it
          })
          test.outputs.dir(resultDirFileProperty.map {
            test.infoln("Roborazzi: Set output dir $it to test task")
            it
          })
          test.outputs.file(resultSummaryFileProperty.map {
            test.infoln("Roborazzi: Set output file $it to test task")
            it
          })
          test.outputs.file(reportFileProperty.map {
            test.infoln("Roborazzi: Set output file $it to test task")
            it
          })

          test.inputs.properties(
            mapOf(
              "isRecordRun" to isRecordRun,
              "isVerifyRun" to isVerifyRun,
              "isCompareRun" to isCompareRun,
              "isVerifyAndRecordRun" to isVerifyAndRecordRun,
              "roborazziProperties" to roborazziProperties,
            )
          )
          test.doFirst {
            val doesRoborazziRun =
              doesRoborazziRunProvider.get()
            if (!doesRoborazziRun) {
              return@doFirst
            }
            test.infoln("Roborazzi: test.doFirst ${test.name}")
            val isTaskPresent =
              isAnyTaskRun(isRecordRun, isVerifyRun, isVerifyAndRecordRun, isCompareRun)
            // Task properties
            if (!isTaskPresent) {
              test.systemProperties.putAll(roborazziProperties)
            } else {
              // Apply other roborazzi properties except for the ones that
              // start with "roborazzi.test"
              test.systemProperties.putAll(
                roborazziProperties.filter { (key, _) ->
                  !key.startsWith("roborazzi.test")
                }
              )
              test.systemProperties["roborazzi.test.record"] =
                isRecordRun.get() || isVerifyAndRecordRun.get()
              test.systemProperties["roborazzi.test.compare"] = isCompareRun.get()
              test.systemProperties["roborazzi.test.verify"] =
                isVerifyRun.get() || isVerifyAndRecordRun.get()
            }

            // Other properties
            test.systemProperties["roborazzi.output.dir"] =
              outputDirRelativePathFromProjectProvider.get()
            if (compareOutputDirProvider.isPresent) {
              test.systemProperties["roborazzi.compare.output.dir"] =
                compareOutputDirProvider.get()
            }
            test.systemProperties["roborazzi.result.dir"] =
              resultDirRelativePathFromProjectProvider.get()
            test.systemProperties["roborazzi.project.path"] =
              projectAbsolutePathProvider.get()
            test.infoln("Roborazzi: Plugin passed system properties " + test.systemProperties + " to the test")
            resultsDir.deleteRecursively()
            resultsDir.mkdirs()
          }
          test.addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor?) {
              // Do nothing
            }

            // The reason why we use afterSuite() instead of task.doLast is that
            // task.doLast is not called if the test is failed.
            // And why we use afterSuite() instead of finalizedBy is that
            // we want to change the tasks' output in the task execution phase.
            override fun afterSuite(suite: TestDescriptor?, result: TestResult?) {
              if (suite?.parent != null) {
                // Run only root suite
                return
              }
              if (doesRoborazziRunProvider.get()) {
                test.infoln("Roborazzi: test.afterSuite ${test.name}")
              } else {
                return
              }

              val results: List<CaptureResult> = resultDirFileTree.get().mapNotNull {
                if (it.name.endsWith(".json")) {
                  CaptureResult.fromJsonFile(it.path)
                } else {
                  null
                }
              }.sortedBy(CaptureResult::goldenFile)
              val resultsSummaryFile = resultSummaryFileProperty.get().asFile

              val roborazziResults = CaptureResults.from(results)
              saveResults(
                test = test,
                resultsSummaryFile = resultsSummaryFile,
                results = results,
                roborazziResults = roborazziResults,
                reportFileProperty = reportFileProperty
              )
              // The reason why we do this in afterSuite() is that we want to change the tasks' output in the task execution phase.
              cleanupOldScreenshotsIfNeeded(
                test = test,
                roborazziProperties = roborazziProperties,
                outputDir = outputDir,
                intermediateDir = intermediateDir,
                roborazziResults = roborazziResults,
              )

              // Copy all files from outputDir to intermediateDir
              // so that we can use Gradle's output caching
              test.infoln("Roborazzi: test.doLast Copy files from ${outputDir.get()} to ${intermediateDir.get()}")
              // outputDir.get().asFileTree.forEach {
              //   println("Copy file ${finalizeTask.absolutePath} to ${intermediateDir.get()}")
              // }
              outputDir.get().asFile.mkdirs()
              outputDir.get().asFile.copyRecursively(
                target = intermediateDir.get().asFile,
                overwrite = true
              )
            }

            override fun beforeTest(testDescriptor: TestDescriptor?) {
              // Do nothing
            }

            override fun afterTest(testDescriptor: TestDescriptor?, result: TestResult?) {
              // Do nothing
            }
          })
          test.finalizedBy(finalizeTestRoborazziTask)
        }

      recordTaskProvider.configure { it.dependsOn(testTaskProvider) }
      compareReportGenerateTaskProvider.configure { it.dependsOn(testTaskProvider) }
      verifyTaskProvider.configure { it.dependsOn(testTaskProvider) }
      verifyAndRecordTaskProvider.configure { it.dependsOn(testTaskProvider) }
    }

    fun AndroidComponentsExtension<*, *, *>.configureComponents() {
      onVariants { variant ->
        val unitTest = variant.unitTest ?: return@onVariants
        val variantSlug = variant.name.capitalizeUS()
        val testVariantSlug = unitTest.name.capitalizeUS()
        val testTaskName = "test$testVariantSlug"
        generateComposePreviewRobolectricTestsIfNeeded(
          project = project,
          variant = variant,
          extension = extension.generateComposePreviewRobolectricTests,
          testTaskProvider = findTestTaskProvider(Test::class, testTaskName)
        )

        // e.g. testDebugUnitTest -> recordRoborazziDebug
        configureRoborazziTasks(
          variantSlug = variantSlug,
          testTaskName = testTaskName,
        )
      }
    }

    project.pluginManager.withPlugin("com.android.application") {
      project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
        .configureComponents()
      verifyGenerateComposePreviewRobolectricTests(
        project = project,
        androidExtension = project.extensions.getByType(TestedExtension::class.java),
        extension = extension.generateComposePreviewRobolectricTests
      )
    }
    project.pluginManager.withPlugin("com.android.library") {
      project.extensions.getByType(LibraryAndroidComponentsExtension::class.java)
        .configureComponents()
      verifyGenerateComposePreviewRobolectricTests(
        project = project,
        androidExtension = project.extensions.getByType(TestedExtension::class.java),
        extension = extension.generateComposePreviewRobolectricTests
      )
    }
    project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
      // e.g. test -> recordRoborazziJvm
      configureRoborazziTasks(
        variantSlug = "Jvm",
        testTaskName = "test",
      )
    }
    project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
      val kotlinMppExtension = checkNotNull(
        project.extensions.findByType(
          KotlinMultiplatformExtension::class.java
        )
      ) { "Kotlin multiplatform plugin not applied!" }
      kotlinMppExtension.targets.all { target ->
        if (target is KotlinJvmTarget) {
          target.testRuns.all { testRun ->
            // e.g. desktopTest -> recordRoborazziDesktop
            configureRoborazziTasks(
              variantSlug = target.name.capitalizeUS(),
              testTaskName = testRun.executionTask.name,
            )
          }
        }
        if (target is KotlinNativeTargetWithTests<*>) {
          target.testRuns.all { testRun: KotlinNativeBinaryTestRun ->
            // e.g. desktopTest -> recordRoborazziDesktop
            configureRoborazziTasks(
              variantSlug = target.name.capitalizeUS(),
              testTaskName = (testRun as ExecutionTaskHolder<*>).executionTask.name,
              testTaskClass = KotlinNativeTest::class
            )
          }
        }
      }
    }
  }

  private fun saveResults(
    test: AbstractTestTask,
    resultsSummaryFile: File,
    results: List<CaptureResult>,
    roborazziResults: CaptureResults,
    reportFileProperty: Provider<RegularFile>
  ) {
    test.infoln("Roborazzi: Save result to ${resultsSummaryFile.absolutePath} with results:${results.size} summary:${roborazziResults.resultSummary}")

    val jsonResult = roborazziResults.toJson()
    resultsSummaryFile.parentFile.mkdirs()
    resultsSummaryFile.writeText(jsonResult)
    val reportFile = reportFileProperty.get().asFile

    reportFile.parentFile.mkdirs()
    WebAssets.create().writeToRoborazziReportsDir(reportFile.parentFile)
    val reportHtml = readIndexHtmlFile()
      ?: throw FileNotFoundException("index.html not found in resources/META-INF folder")
    reportFile.writeText(
      reportHtml.replace(
        oldValue = "REPORT_TEMPLATE_BODY",
        newValue = roborazziResults.toHtml(reportFile.parentFile.absolutePath)
      )
    )
  }

  private fun cleanupOldScreenshotsIfNeeded(
    test: AbstractTestTask,
    roborazziProperties: Map<String, Any?>,
    outputDir: DirectoryProperty,
    intermediateDir: DirectoryProperty,
    roborazziResults: CaptureResults,
  ) {
    val isCleanupRun = roborazziProperties["roborazzi.cleanupOldScreenshots"] == "true"
    val isRecordRun = test.systemProperties["roborazzi.test.record"] == true

    if (isCleanupRun || isRecordRun) {
      // Delete all images from the intermediateDir
      intermediateDir.get().asFile.walkTopDown().forEach { file ->
        if (KnownImageFileExtensions.contains(file.extension)) {
          file.delete()
        }
      }
    }

    if (isCleanupRun) {
      // Remove all files not in the results from the outputDir
      val removingFiles: MutableSet<String> = outputDir.get().asFile
        .walkTopDown()
        .toList()
        .filter { it.isFile && KnownImageFileExtensions.contains(it.extension) }
        .map { it.absolutePath }
        .toMutableSet()
      roborazziResults.captureResults.forEach { result ->
        val latestFiles = listOfNotNull(
          result.actualFile,
          result.compareFile,
          result.goldenFile
        ).map { File(it).absolutePath }
        removingFiles.removeAll(latestFiles)
      }
      test.infoln("Roborazzi: Cleanup old files $removingFiles")
      removingFiles.forEach { file ->
        File(file).delete()
      }
    }
  }

  private fun readIndexHtmlFile(): String? {
    return this::class.java.classLoader.getResource("META-INF/index.html")?.readText()
  }

  private fun String.capitalizeUS() =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

  abstract class RestoreOutputDirRoborazziTask @Inject constructor(objects: ObjectFactory) :
    DefaultTask() {
    @get:InputDirectory
    @Optional
    val inputDir: DirectoryProperty = objects.directoryProperty()

    @get:OutputDirectory
    val outputDir: DirectoryProperty = objects.directoryProperty()

    @TaskAction
    fun copy() {
      val outputDirFile = outputDir.get().asFile
      if (outputDirFile.exists() && outputDirFile.listFiles().isNotEmpty()) return
      this.infoln("Roborazzi RestoreOutputDirRoborazziTask: Copy files from ${inputDir.get()} to ${outputDirFile}")
      inputDir.get().asFile.copyRecursively(outputDirFile)
    }
  }

  open class RoborazziTask : DefaultTask() {
    @Option(
      option = "tests",
      description = "Sets test class or method name to be included, '*' is supported."
    )
    open fun setTestNameIncludePatterns(testNamePattern: List<String>): RoborazziTask {
      project.tasks.withType(Test::class.java).configureEach {
        it.setTestNameIncludePatterns(testNamePattern)
      }
      return this
    }
  }
}

fun Task.infoln(format: String) =
  logger.info(format)
