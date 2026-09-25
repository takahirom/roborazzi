package io.github.takahirom.roborazzi

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * `generateComposePreviewDesktopTests.sceneReuse` captures the previews that need the same Compose
 * scene without closing it in between.
 *
 * It is meant to be invisible apart from the time it saves, so these tests pin the two things that
 * make it invisible: the images have to come out byte for byte the same as the per-scene run, and
 * every preview has to stay its own reported test under the name `Parameterized` gave it.
 */
class DesktopPreviewSceneReuseGenerateTest {
  @get:Rule
  val testProjectDir = TemporaryFolder()

  @Test
  fun whenSceneReuseIsOffTheGeneratedTestIsTheParameterizedOneAsBefore() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      buildGradle.generatedTestClassCount = 2

      record()

      listOf(0, 1).forEach { shardIndex ->
        val source = generatedTestClassText("RoborazziDesktopPreviewParameterizedTests$shardIndex")
        assert(source.contains("@RunWith(Parameterized::class)")) {
          "Scene reuse is off, so shard $shardIndex should still be a Parameterized test:\n$source"
        }
        assert(source.contains("filterIndexed { index, _ -> index % 2 == $shardIndex }")) {
          "Scene reuse is off, so shard $shardIndex should shard the way it always has:\n$source"
        }
        assert(!source.contains("SceneReuse")) {
          "Scene reuse is off, so shard $shardIndex should not mention it at all:\n$source"
        }
      }
    }
  }

  @Test
  fun whenSceneReuseIsOnTheGeneratedTestUsesTheSceneReuseRunner() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      buildGradle.sceneReuse = true

      record()

      val source = generatedTestClassText("RoborazziDesktopPreviewParameterizedTests")
      assert(source.contains("@RunWith(DesktopPreviewSceneReuseRunner::class)")) {
        "Expected the scene-reusing runner in the generated test:\n$source"
      }
      // Parameterized cannot hold a scene open across test methods, so the two must not be mixed.
      // (The generated class name still contains the word, so match the runner, not the name.)
      assert(!source.contains("Parameterized::class") && !source.contains("@Parameterized")) {
        "The scene-reusing test should not also be a Parameterized test:\n$source"
      }
    }
  }

  @Test
  fun whenSceneReuseIsOnTheRecordedImagesAreIdenticalToThePerSceneRun() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      record(additionalParameters = NO_BUILD_CACHE)
      val perScene = recordedImageBytes()
      assert(perScene.isNotEmpty()) { "The per-scene run recorded nothing to compare against" }

      buildGradle.sceneReuse = true
      clearRecordedImages()
      record(additionalParameters = NO_BUILD_CACHE)
      val reused = recordedImageBytes()

      assert(reused.keys == perScene.keys) {
        "Scene reuse changed which previews were captured. Only in the per-scene run: " +
          "${perScene.keys - reused.keys}; only in the reusing run: ${reused.keys - perScene.keys}"
      }
      val different = perScene.keys.filter { !perScene.getValue(it).contentEquals(reused.getValue(it)) }
      assert(different.isEmpty()) {
        "Scene reuse changed what these previews render: $different"
      }
    }
  }

  @Test
  fun whenSceneReuseIsOnEveryPreviewIsStillReportedUnderItsOwnName() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      record(additionalParameters = NO_BUILD_CACHE)
      val perScene = reportedTestCaseNames()

      buildGradle.sceneReuse = true
      record(additionalParameters = NO_BUILD_CACHE)

      assert(reportedTestCaseNames() == perScene) {
        "Scene reuse changed the reported test names, which breaks --tests filters and report " +
          "diffs. Only per-scene: ${perScene - reportedTestCaseNames()}; only reusing: " +
          "${reportedTestCaseNames() - perScene}"
      }
    }
  }

  @Test
  fun whenSceneReuseIsOnAndShardedEveryPreviewStillRunsExactlyOnce() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      record(additionalParameters = NO_BUILD_CACHE)
      val singleClass = reportedTestCaseNames()
      val singleClassCount = executedTestCount("desktopTest")

      buildGradle.sceneReuse = true
      buildGradle.generatedTestClassCount = 2
      record(additionalParameters = NO_BUILD_CACHE)

      checkGeneratedTestClassCount(2)
      // Shards are contiguous slices of one sorted list, so a preview falling in two of them, or in
      // none, is the failure this guards against.
      assert(executedTestCount("desktopTest") == singleClassCount) {
        "Two shards ran ${executedTestCount("desktopTest")} tests, but one class ran $singleClassCount"
      }
      assert(reportedTestCaseNames() == singleClass) {
        "Sharding changed which previews ran. Missing: ${singleClass - reportedTestCaseNames()}; " +
          "unexpected: ${reportedTestCaseNames() - singleClass}"
      }
      // The count and the name set above are both totals, and two shards that each ran the same
      // preview twice while dropping another would satisfy them together. The reports are per
      // class, so overlap between them is what actually rules that out.
      val byShard = reportedTestCaseNamesByReport()
      assert(byShard.size == 2) {
        "Expected one report per shard, but got ${byShard.keys}"
      }
      val duplicated = byShard.values.flatten().groupingBy { it }.eachCount()
        .filterValues { it > 1 }.keys
      assert(duplicated.isEmpty()) {
        "These previews were run by more than one shard: $duplicated"
      }
    }
  }

  @Test
  fun whenOnePreviewInASharedSceneFailsTheOthersStillRun() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      buildGradle.sceneReuse = true
      record(additionalParameters = NO_BUILD_CACHE)
      val recorded = reportedTestCaseNames()

      // In verify mode a mismatch throws out of captureRoboImage. Several previews share one
      // runDesktopComposeUiTest call now, so without per-capture reporting this one throw would end
      // the whole group and the rest would never be captured.
      corruptRecordedImage("PreviewNormal")
      verify(buildType = BuildType.BuildAndFail, additionalParameters = NO_BUILD_CACHE)

      assert(reportedTestCaseNames() == recorded) {
        "A failing preview should not stop the rest of its scene from being reported. Missing: " +
          "${recorded - reportedTestCaseNames()}"
      }
      val failed = failedTestCaseNames()
      assert(failed.size == 1 && failed.single().contains("PreviewNormal")) {
        "Expected only the corrupted preview to fail, but these did: $failed"
      }
    }
  }

  @Test
  fun whenOnePreviewHasAnUnparsableDeviceTheOthersStillRun() {
    DesktopPreviewModule(RoborazziGradleRootProject(testProjectDir), testProjectDir).apply {
      buildGradle.sceneReuse = true
      testProjectDir.root
        .resolve(
          "${DesktopPreviewModule.moduleName}/src/commonMain/kotlin/" +
            "com/github/takahirom/preview/tests/UnparsableDevicePreview.kt"
        )
        .writeText(
          """
            package com.github.takahirom.preview.tests

            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview

            @Preview(device = "not-a-device-spec")
            @Composable
            fun PreviewUnparsableDevice() {
              Text("unparsable device")
            }
          """.trimIndent()
        )

      // The device is resolved when the preview is captured. Resolving it before the shard's
      // captures are handed to JUnit would throw once for the whole shard, and none of the other
      // previews would be reported.
      record(buildType = BuildType.BuildAndFail, additionalParameters = NO_BUILD_CACHE)

      val failed = failedTestCaseNames()
      assert(failed.size == 1 && failed.single().contains("PreviewUnparsableDevice")) {
        "Expected only the preview with the unparsable device to fail, but these did: $failed"
      }
      assert((reportedTestCaseNames() - failed).isNotEmpty()) {
        "The other previews of the shard should still be reported, but only $failed was"
      }
      checkHasImageContaining("PreviewNormal")
    }
  }
}
