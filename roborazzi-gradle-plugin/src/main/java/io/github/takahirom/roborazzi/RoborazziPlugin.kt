package io.github.takahirom.roborazzi

import android.util.JsonWriter
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import java.util.Locale
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
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

    val hasLibraryPlugin = project.pluginManager.hasPlugin("com.android.library")
    val variants = if (hasLibraryPlugin) {
      project.extensions.getByType(LibraryExtension::class.java)
        .libraryVariants
    } else {
      project.extensions.getByType(BaseAppModuleExtension::class.java)
        .applicationVariants
    }
    variants.all { variant ->
      val variantSlug = variant.name.capitalize(Locale.US)

//      val reportOutputDir = project.layout.buildDirectory.dir("reports/roborazzi")
//      val snapshotOutputDir = project.layout.projectDirectory.dir("src/test/snapshots")

      val testVariantSlug = variant.unitTestVariant.name.capitalize(Locale.US)

      val recordTaskProvider =
        project.tasks.register("recordRoborazzi$variantSlug", RoborazziTask::class.java) {
          it.group = VERIFICATION_GROUP
        }
      recordVariants.configure { it.dependsOn(recordTaskProvider) }

      val compareReportGenerateTaskProvider =
        project.tasks.register(
          "compareRoborazzi$variantSlug",
          CompareReportGenerateTask::class.java
        ) {
          it.group = VERIFICATION_GROUP
          it.inputDir.set(project.file(RoborazziReportConst.compareReportDirPath))
          it.outputFile.set(project.file(RoborazziReportConst.compareSummaryReportFilePath))
        }
      compareVariants.configure { it.dependsOn(compareReportGenerateTaskProvider) }

      val verifyTaskProvider =
        project.tasks.register("verifyRoborazzi$variantSlug", RoborazziTask::class.java) {
          it.group = VERIFICATION_GROUP
        }
      verifyVariants.configure { it.dependsOn(verifyTaskProvider) }

      val isRecordRun = project.objects.property(Boolean::class.java)
      val isVerifyRun = project.objects.property(Boolean::class.java)
      val isCompareRun = project.objects.property(Boolean::class.java)

      project.gradle.taskGraph.whenReady { graph ->
        isRecordRun.set(recordTaskProvider.map { graph.hasTask(it) })
        isVerifyRun.set(verifyTaskProvider.map { graph.hasTask(it) })
        isCompareRun.set(compareReportGenerateTaskProvider.map { graph.hasTask(it) })
      }

      val testTaskProvider = project.tasks.named("test$testVariantSlug", Test::class.java) { test ->
        test.systemProperties["roborazzi.build.dir"] =
          project.layout.buildDirectory.get().toString()

//        test.outputs.dir(reportOutputDir)
//        test.outputs.dir(snapshotOutputDir)

        val roborazziProperties =
          project.properties.filterKeys { it.startsWith("io.github.takahirom.roborazzi") }
        val compareReportDir = project.file(RoborazziReportConst.compareReportDirPath)

        test.doFirst {
          test.systemProperties["roborazzi.test.record"] = isRecordRun.get()
          test.systemProperties["roborazzi.test.compare"] = isCompareRun.get()
          test.systemProperties["roborazzi.test.verify"] = isVerifyRun.get()
          println("Roborazzi roborazziProperties:"+roborazziProperties)
          test.systemProperties.putAll(roborazziProperties)
          if (isCompareRun.get()) {
            compareReportDir.deleteRecursively()
            compareReportDir.mkdirs()
          }
        }
      }

      recordTaskProvider.configure { it.dependsOn(testTaskProvider) }
      compareReportGenerateTaskProvider.configure { it.dependsOn(testTaskProvider) }
      verifyTaskProvider.configure { it.dependsOn(testTaskProvider) }
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

  abstract class CompareReportGenerateTask : RoborazziTask() {

    @get:InputDirectory
    abstract val inputDir: DirectoryProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun doWork() {
      val results: List<CompareReportCaptureResult> =
        inputDir.asFileTree.mapNotNull {
          if (it.name.endsWith(".json")) {
            CompareReportCaptureResult.fromJsonFile(it.path)
          } else {
            null
          }
        }
      val reportFile =
        outputFile.asFile.get()
      println("Save report to ${reportFile.absolutePath} with results:${results.size}")

      val jsonWriter = JsonWriter(
        reportFile.writer()
      )
      jsonWriter.use { writer ->
        writer.beginObject()

        writer.name("summary").beginObject()
        writer.name("total").value(results.size)
        writer.name("added").value(results.count { it is CompareReportCaptureResult.Added })
        writer.name("changed").value(results.count { it is CompareReportCaptureResult.Changed })
        writer.name("unchanged").value(results.count { it is CompareReportCaptureResult.Unchanged })
        writer.endObject()

        writer.name("results").beginArray()
        results.forEach { result ->
          result.writeJson(writer)
        }
        writer.endArray()
        writer.endObject()
      }
    }
  }
}
