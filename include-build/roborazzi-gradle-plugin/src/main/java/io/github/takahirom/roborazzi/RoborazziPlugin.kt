package io.github.takahirom.roborazzi

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.github.takahirom.roborazzi.CaptureResult
import com.github.takahirom.roborazzi.CaptureResults
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziReportConst
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.Test
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskSuccessResult
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.ExecutionTaskHolder
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithTests
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.targets.native.KotlinNativeBinaryTestRun
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.reflect.KClass

private const val DEFAULT_OUTPUT_DIR = "outputs/roborazzi"
private const val DEFAULT_TEMP_DIR = "intermediates/roborazzi"

/**
 * Experimental API
 * This class can be changed without notice.
 */
open class RoborazziExtension @Inject constructor(objects: ObjectFactory) {
  val outputDir: DirectoryProperty = objects.directoryProperty()
}

@Suppress("unused")
// From Paparazzi: https://github.com/cashapp/paparazzi/blob/a76702744a7f380480f323ffda124e845f2733aa/paparazzi/paparazzi-gradle-plugin/src/main/java/app/cash/paparazzi/gradle/PaparazziPlugin.kt
abstract class RoborazziPlugin : Plugin<Project> {
  @Inject abstract fun getEventsListenerRegistry(): BuildEventsListenerRegistry

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

  @OptIn(InternalRoborazziApi::class)
  override fun apply(project: Project) {
    val extension = project.extensions.create("roborazzi", RoborazziExtension::class.java)

    val verifyVariants = project.tasks.register("verifyRoborazzi")
    val compareVariants = project.tasks.register("compareRoborazzi")
    val recordVariants = project.tasks.register("recordRoborazzi")
    val verifyAndRecordVariants = project.tasks.register("verifyAndRecordRoborazzi")

    // For fixing unexpected skip test
    val outputDir =
      extension.outputDir.convention(project.layout.buildDirectory.dir(DEFAULT_OUTPUT_DIR))
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

    fun configureRoborazziTasks(
      variantSlug: String,
      testTaskName: String,
      testTaskSkipEventsServiceProvider: Provider<TestTaskSkipEventsServiceProvider>,
      testTaskClass: KClass<out AbstractTestTask> = Test::class
    ) {
      try {
        testTaskSkipEventsServiceProvider.get().addExpectingTestTaskName(testTaskName)
      } catch (e: ClassCastException) {
        throw IllegalStateException(
          """You should use `id("io.github.takahirom.roborazzi") version "[version]" apply false` in the root project 
            |to ensure the build cache property functions correctly. 
            |This is a temporary workaround, 
            |and we are awaiting a permanent fix from the Gradle core.
            |https://github.com/takahirom/roborazzi/issues/266""".trimMargin(),
          e
        )
      }
      val testTaskOutputDirForEachVariant: DirectoryProperty = project.objects.directoryProperty()
      val intermediateDirForEachVariant =
        testTaskOutputDirForEachVariant.convention(
          project.layout.buildDirectory.dir(
            DEFAULT_TEMP_DIR
          )
        )

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

      val testTaskProvider = project.tasks.withType(testTaskClass.java)
        .matching {
          it.name == testTaskName
        }
      val roborazziProperties: Map<String, Any?> =
        project.properties.filterKeys { it != "roborazzi" && it.startsWith("roborazzi") }

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

      val finalizeTestRoborazziTask = project.tasks.register(
        /* name = */ "finalizeTestRoborazzi$variantSlug",
        /* configurationAction = */ object : Action<Task> {
          override fun execute(finalizeTestTask: Task) {
            finalizeTestTask.onlyIf {
              val doesRoborazziRun = doesRoborazziRunProvider.get()
              finalizeTestTask.infoln("Roborazzi: roborazziTestFinalizer.onlyIf doesRoborazziRun $doesRoborazziRun")
              doesRoborazziRun
            }
            val taskPath = if (project.path == ":") {
              ":"
            } else {
              project.path + ":"
            }
            finalizeTestTask.doLast {
              val taskSkipEventsService = testTaskSkipEventsServiceProvider.get()
              val buildFinishCountDownLatch = taskSkipEventsService.countDownLatch
              val currentIsTestSkipped =
                taskSkipEventsService.skippedTestTaskMap[taskPath + testTaskName]
              val isTestSkipped = if (currentIsTestSkipped == null) {
                // BuildService could cause race condition
                // https://github.com/gradle/gradle/issues/24887
                finalizeTestTask.infoln("Roborazzi: roborazziTestFinalizer.doLast test task result doesn't exits. currentIsTestSkipped:$currentIsTestSkipped currentTimeMillis:${System.currentTimeMillis()}")
                val waitSuccess = buildFinishCountDownLatch.await(200, TimeUnit.MILLISECONDS)
                val new =
                  taskSkipEventsService.skippedTestTaskMap[taskPath + testTaskName]
                finalizeTestTask.infoln("Roborazzi: roborazziTestFinalizer.doLast wait end currentIsTestSkipped:$currentIsTestSkipped new:$new waitSuccess:$waitSuccess currentTimeMillis:${System.currentTimeMillis()}")
                new ?: false
              } else {
                currentIsTestSkipped
              }
              finalizeTestTask.infoln("Roborazzi: roborazziTestFinalizer.doLast isTestSkipped:$isTestSkipped")
              if (isTestSkipped) {
                // If the test is skipped, we need to use cached files
                finalizeTestTask.infoln("Roborazzi: finalizeTestRoborazziTask isTestSkipped:$isTestSkipped Copy files from ${intermediateDir.get()} to ${outputDir.get()}")
                intermediateDir.get().asFile.mkdirs()
                intermediateDir.get().asFile.copyRecursively(
                  target = outputDir.get().asFile,
                  overwrite = true
                )
              }

              val results: List<CaptureResult> = resultDirFileTree.get().mapNotNull {
                if (it.name.endsWith(".json")) {
                  CaptureResult.fromJsonFile(it.path)
                } else {
                  null
                }
              }
              val resultsSummaryFile = resultSummaryFileProperty.get().asFile

              val roborazziResults = CaptureResults.from(results)
              finalizeTestTask.infoln("Roborazzi: Save result to ${resultsSummaryFile.absolutePath} with results:${results.size} summary:${roborazziResults.resultSummary}")

              val jsonResult = roborazziResults.toJson()
              resultsSummaryFile.parentFile.mkdirs()
              resultsSummaryFile.writeText(jsonResult)
              val reportFile = reportFileProperty.get().asFile
              reportFile.parentFile.mkdirs()
              reportFile.writeText(
                RoborazziReportConst.reportHtml.replace(
                  oldValue = "REPORT_TEMPLATE_BODY",
                  newValue = roborazziResults.toHtml(reportFile.parentFile.absolutePath)
                )
              )
            }
          }
        })
      testTaskProvider
        .configureEach { test ->
          val resultsDir = resultDirFileProperty.get().asFile
          if (restoreOutputDirRoborazziTaskProvider.isPresent) {
            test.inputs.dir(restoreOutputDirRoborazziTaskProvider.map {
              if (!it.outputDir.get().asFile.exists()) {
                it.outputDir.get().asFile.mkdirs()
              }
              test.infoln("Roborazzi: Set input dir ${it.outputDir.get()} to test task")
              it.outputDir
            })
          } else {
            test.inputs.dir(outputDir.map {
              if (!it.asFile.exists()) {
                it.asFile.mkdirs()
              }
              test.infoln("Roborazzi: Set input dir $it to test task")
              it
            })
          }
          test.outputs.dir(intermediateDirForEachVariant.map {
            test.infoln("Roborazzi: Set output dir $it to test task")
            it
          })
          test.outputs.dir(resultDirFileProperty.let {
            test.infoln("Roborazzi: Set output dir $it to test task")
            it
          })
          test.outputs.file(resultSummaryFileProperty.let {
            test.infoln("Roborazzi: Set output file $it to test task")
            it
          })
          test.outputs.file(reportFileProperty.let {
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
//          test.outputs.upToDateWhen {
//            val doesRoborazziRun = doesRoborazziRunProvider.get()
//            val inputDirEmpty = outputDir.get().asFile.listFiles()?.isEmpty() ?: true
//            test.infoln("Roborazzi: test.outputs.upToDateWhen !(doesRoborazziRun:$doesRoborazziRun && inputDirEmpty:$inputDirEmpty)")
//            !(doesRoborazziRun && inputDirEmpty)
//          }
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
            test.systemProperties["roborazzi.result.dir"] =
              resultDirRelativePathFromProjectProvider.get()
            test.systemProperties["roborazzi.project.path"] =
              projectAbsolutePathProvider.get()
            test.infoln("Roborazzi: Plugin passed system properties " + test.systemProperties + " to the test")
            resultsDir.deleteRecursively()
            resultsDir.mkdirs()
          }
          test.doLast {
            // Copy all files from outputDir to intermediateDir
            // so that we can use Gradle's output caching
            it.infoln("Roborazzi: test.doLast Copy files from ${outputDir.get()} to ${intermediateDir.get()}")
            // outputDir.get().asFileTree.forEach {
            //   println("Copy file ${finalizeTask.absolutePath} to ${intermediateDir.get()}")
            // }
            outputDir.get().asFile.mkdirs()
            outputDir.get().asFile.copyRecursively(
              target = intermediateDir.get().asFile,
              overwrite = true
            )
          }
          test.finalizedBy(finalizeTestRoborazziTask)
        }

      recordTaskProvider.configure { it.dependsOn(testTaskProvider) }
      compareReportGenerateTaskProvider.configure { it.dependsOn(testTaskProvider) }
      verifyTaskProvider.configure { it.dependsOn(testTaskProvider) }
      verifyAndRecordTaskProvider.configure { it.dependsOn(testTaskProvider) }
    }

    val testTaskSkipEventsServiceProvider: Provider<TestTaskSkipEventsServiceProvider> =
      project.gradle.sharedServices.registerIfAbsent(
        "roborazziTestTaskEvents", TestTaskSkipEventsServiceProvider::class.java
      ) { spec ->
        // do nothing
      }
    getEventsListenerRegistry().onTaskCompletion(testTaskSkipEventsServiceProvider)

    fun AndroidComponentsExtension<*, *, *>.configureComponents() {
      onVariants { variant ->
        val unitTest = variant.unitTest ?: return@onVariants
        val variantSlug = variant.name.capitalizeUS()
        val testVariantSlug = unitTest.name.capitalizeUS()

        // e.g. testDebugUnitTest -> recordRoborazziDebug
        configureRoborazziTasks(
          variantSlug = variantSlug,
          testTaskName = "test$testVariantSlug",
          testTaskSkipEventsServiceProvider = testTaskSkipEventsServiceProvider
        )
      }
    }

    project.pluginManager.withPlugin("com.android.application") {
      project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
        .configureComponents()
    }
    project.pluginManager.withPlugin("com.android.library") {
      project.extensions.getByType(LibraryAndroidComponentsExtension::class.java)
        .configureComponents()
    }
    project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
      // e.g. test -> recordRoborazziJvm
      configureRoborazziTasks(
        variantSlug = "Jvm",
        testTaskName = "test",
        testTaskSkipEventsServiceProvider = testTaskSkipEventsServiceProvider
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
              testTaskSkipEventsServiceProvider = testTaskSkipEventsServiceProvider
            )
          }
        }
        if (target is KotlinNativeTargetWithTests<*>) {
          target.testRuns.all { testRun: KotlinNativeBinaryTestRun ->
            // e.g. desktopTest -> recordRoborazziDesktop
            configureRoborazziTasks(
              variantSlug = target.name.capitalizeUS(),
              testTaskName = (testRun as ExecutionTaskHolder<*>).executionTask.name,
              testTaskSkipEventsServiceProvider = testTaskSkipEventsServiceProvider,
              testTaskClass = KotlinNativeTest::class
            )
          }
        }
      }
    }
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

/**
 * We can't get whether the test is skipped or not from the test task itself
 * because of the configuration cache
 */
abstract class TestTaskSkipEventsServiceProvider : BuildService<BuildServiceParameters.None?>,
  OperationCompletionListener {
  val skippedTestTaskMap = mutableMapOf<String, Boolean>()
  var countDownLatch = CountDownLatch(1)
    private set
  private val logger = Logging.getLogger(this::class.java)
  private val expectingTestNames = mutableListOf<String>()
  fun addExpectingTestTaskName(testName: String) {
    expectingTestNames.add(testName)
  }

  override fun onFinish(finishEvent: FinishEvent) {
    val displayName = finishEvent.displayName
//    println(
//      "Roborazzi: onFinish " +
//        "expectingTestNames:$expectingTestNames" +
//        "displayName:$displayName " +
//        "finishEvent:$finishEvent " +
//        "finishEvent.descriptor:${finishEvent.descriptor}" +
//        "finishEvent.descriptor.name:${finishEvent.descriptor.name}"
//    )
    if (finishEvent !is TaskFinishEvent) {
      return
    }
    if (!expectingTestNames.any {
        displayName.contains(it, ignoreCase = true)
      }) {
      return
    }
    val result = finishEvent.result
    if (when (result) {
        is TaskSuccessResult -> {
          if (result.isFromCache) {
            true
          } else if (result.isUpToDate) {
            true
          } else {
            false
          }
        }

        is TaskFailureResult -> false
        is TaskSkippedResult -> true
        else -> false
      }
    ) {
      logger.info("Roborazzi: Skip test ${finishEvent.descriptor.name} ${System.currentTimeMillis()}")
      skippedTestTaskMap[finishEvent.descriptor.name] = true
    } else {
      logger.info("Roborazzi: Don't skip test ${finishEvent.descriptor.name} ${System.currentTimeMillis()}")
      skippedTestTaskMap[finishEvent.descriptor.name] = false
    }
    countDownLatch.countDown()
    countDownLatch = CountDownLatch(1)
  }
}

fun Task.infoln(format: String) =
  logger.info(format)
