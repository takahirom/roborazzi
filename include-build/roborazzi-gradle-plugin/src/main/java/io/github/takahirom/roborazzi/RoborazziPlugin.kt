package io.github.takahirom.roborazzi

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import java.io.File
import java.util.Locale
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP

@Suppress("unused")
// From Paparazzi: https://github.com/cashapp/paparazzi/blob/a76702744a7f380480f323ffda124e845f2733aa/paparazzi/paparazzi-gradle-plugin/src/main/java/app/cash/paparazzi/gradle/PaparazziPlugin.kt
class RoborazziPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val verifyVariants = project.tasks.register("verifyRoborazzi")
    val compareVariants = project.tasks.register("compareRoborazzi")
    val recordVariants = project.tasks.register("recordRoborazzi")
    val verifyAndRecordVariants = project.tasks.register("verifyAndRecordRoborazzi")

    // For fixing unexpected skip test
    val defaultOutputDir = "build/outputs/roborazzi"
    val generateOutputDirTaskProvider =
      project.tasks.register<GenerateOutputDirRoborazziTask>(
        "generateDefaultRoborazziOutputDir",
        GenerateOutputDirRoborazziTask::class.java
      ) {
        it.group = VERIFICATION_GROUP
        it.inputDir.set(project.layout.projectDirectory.dir(defaultOutputDir))
        it.outputDir.set(project.layout.projectDirectory.dir(defaultOutputDir))
      }

    fun AndroidComponentsExtension<*, *, *>.configureComponents() {
      onVariants { variant ->
        val unitTest = variant.unitTest ?: return@onVariants
        val variantSlug = variant.name.capitalizeUS()

//      val reportOutputDir = project.layout.buildDirectory.dir("reports/roborazzi")
//      val snapshotOutputDir = project.layout.projectDirectory.dir("src/test/snapshots")

        val testVariantSlug = unitTest.name.capitalizeUS()

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

        val testTaskProvider = project.tasks.withType(Test::class.java)
          .matching { it.name == "test$testVariantSlug" }
        testTaskProvider
          .configureEach { test ->
            //        test.outputs.dir(reportOutputDir)
            //        test.outputs.dir(snapshotOutputDir)

            val roborazziProperties =
              project.properties.filterKeys { it.startsWith("roborazzi") }
            val compareReportDir = project.file(RoborazziReportConst.compareReportDirPath)
            val compareReportDirFileTree =
              project.fileTree(RoborazziReportConst.compareReportDirPath)
            val compareSummaryReportFile =
              project.file(RoborazziReportConst.compareSummaryReportFilePath)
            test.dependsOn(generateOutputDirTaskProvider)
            test.outputs.dir(defaultOutputDir)

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
              val isTaskPresent =
                isRecordRun.get() || isVerifyRun.get() || isCompareRun.get() || isVerifyAndRecordRun.get()
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
              if (test.systemProperties["roborazzi.test.compare"]?.toString()
                  ?.toBoolean() == true
              ) {
                compareReportDir.deleteRecursively()
                compareReportDir.mkdirs()
              }
            }
            // We don't use custom task action here because we want to run it even if we use `-P` parameter
            test.doLast {
              val isCompare =
                test.systemProperties["roborazzi.test.compare"]?.toString()?.toBoolean() == true
              if (!isCompare) {
                return@doLast
              }
              val results: List<CompareReportCaptureResult> = compareReportDirFileTree.mapNotNull {
                if (it.name.endsWith(".json")) {
                  CompareReportCaptureResult.fromJsonFile(it.path)
                } else {
                  null
                }
              }
              println("Save report to ${compareSummaryReportFile.absolutePath} with results:${results.size}")

              val reportResult = CompareReportResult(
                summary = CompareSummary(
                  total = results.size,
                  added = results.count { it is CompareReportCaptureResult.Added },
                  changed = results.count { it is CompareReportCaptureResult.Changed },
                  unchanged = results.count { it is CompareReportCaptureResult.Unchanged }
                ),
                compareReportCaptureResults = results
              )

              val jsonResult = reportResult.toJson()
              compareSummaryReportFile.writeText(jsonResult.toString())
            }
          }

        recordTaskProvider.configure { it.dependsOn(testTaskProvider) }
        compareReportGenerateTaskProvider.configure { it.dependsOn(testTaskProvider) }
        verifyTaskProvider.configure { it.dependsOn(testTaskProvider) }
        verifyAndRecordTaskProvider.configure { it.dependsOn(testTaskProvider) }
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
  }

  private fun String.capitalizeUS() =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }


  abstract class GenerateOutputDirRoborazziTask : DefaultTask() {
    @get:InputDirectory
    @Optional
    val inputDir: DirectoryProperty? = project.objects.directoryProperty()

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generateOutputDir() {
      val outputDir = outputDir.get().asFile
      if (!outputDir.exists()) {
        outputDir.mkdirs()
      }
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
