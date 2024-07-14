package io.github.takahirom.roborazzi

import org.gradle.testkit.runner.BuildResult
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreviewGenerateTest {
  @get:Rule
  val testProjectDir = TemporaryFolder()

  @Test
  fun whenRecordRunImagesShouldBeRecorded() {
    RoborazziGradleRootProject(testProjectDir).previewModule.apply {
      record()

      checkHasImages()
    }
  }
}

class PreviewModule(
  val rootProject: RoborazziGradleRootProject,
  val testProjectDir: TemporaryFolder
) {
  val moduleName = "sample-generate-preview-tests"

  fun record() {
    runTask("recordRoborazziDebug")
  }

  private fun runTask(
    task: String,
    buildType: BuildType = BuildType.Build,
    additionalParameters: Array<String> = arrayOf()
  ): BuildResult {
    val buildResult = rootProject.runTask(
      ":$moduleName:" + task,
      buildType,
      additionalParameters
    )
    println(
      "app/output/roborazzi/ list files:" + testProjectDir.root.resolve("app/output/roborazzi/")
        .listFiles()
    )
    return buildResult
  }

  fun checkHasImages() {
    val images = testProjectDir.root.resolve("app/output/$moduleName/").listFiles()
    println("images:" + images?.toList())
    assert(images?.isNotEmpty() == true)
  }
}