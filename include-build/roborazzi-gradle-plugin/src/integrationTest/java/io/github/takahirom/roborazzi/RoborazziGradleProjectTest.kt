package io.github.takahirom.roborazzi

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Run this test with `cd include-build` and `./gradlew roborazzi-gradle-plugin:check`
 * You can also run this test with the following command:
 * ./gradlew roborazzi-gradle-plugin:integrationTest --tests "*RoborazziGradleProjectTest.record"
 */
class RoborazziGradleProjectTest {

  @get:Rule
  val testProjectDir = TemporaryFolder()

  private val className = "com.github.takahirom.integration_test_project.RoborazziTest"

  private var defaultBuildDir = "build"

  private val screenshotAndName
    get() =
      "app/$defaultBuildDir/outputs/roborazzi/$className"

  private val defaultRoborazziOutputDir get() = "$defaultBuildDir/outputs/roborazzi"
  private val customReferenceScreenshotAndName
    get() =
      "app/$defaultRoborazziOutputDir/customdir/custom_file"
  private val customCompareScreenshotAndName
    get() =
      "app/$defaultRoborazziOutputDir/custom_compare_outputDirectoryPath/custom_file"
  private val customReferenceScreenshotAndNameWithRoborazziContext
    get() =
      "app/$defaultRoborazziOutputDir/custom_outputDirectoryPath_from_rule/$defaultRoborazziOutputDir/customdir/custom_file"
  private val customCompareScreenshotAndNameWithRoborazziContext
    get() =
      "app/$defaultRoborazziOutputDir/custom_compare_outputDirectoryPath/custom_file"

  private val addedScreenshotAndName
    get() =
      "app/$defaultRoborazziOutputDir/com.github.takahirom.integration_test_project.AddedRoborazziTest"

  private val resultFileSuffix get() = "$className.testCapture.json"

  @Test
  fun record() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      record()

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkResultFileExists(resultFileSuffix)
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun clear() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      record()

      clear()

      checkResultsSummaryFileExists()
      checkRecordedFileNotExists("$screenshotAndName.testCapture.png")
      checkResultFileExists(resultFileSuffix)
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }


  @Test
  fun whenRecordAndRemovedOutputAndRecordThenSkipAndRestoreTheImages() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      record()
      removeRoborazziOutputDir()
      val output = record().output
      assertSkipped(output)

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun recordWhenRemovedOutputAndIntermediate() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      removeRoborazziAndIntermediateOutputDir()
      record()
      removeRoborazziAndIntermediateOutputDir()
      val output = record().output
      assertFromCache(output)

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun recordWhenRunTwice() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      val output1 = record().output
      assertNotSkipped(output1)
      val output2 = record().output
      assertSkipped(output2)

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun recordWithSystemParameterWhenRemovedOutputAndIntermediate() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      val output1 = recordWithSystemParameter().output
      assertNotSkipped(output1)
      removeRoborazziAndIntermediateOutputDir()
      val output2 = recordWithSystemParameter().output
      assertFromCache(output2)

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun recordWhenRunTwiceWithGradleCustomOutput() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      val customDirFromGradle = "src/screenshots/roborazzi_customdir_from_gradle"
      buildGradle.customOutputDirPath = customDirFromGradle
      val output1 = record().output
      assertNotSkipped(output1)
      val output2 = record().output
      assertSkipped(output2)

      checkResultsSummaryFileExists()
      checkRecordedFileExists("app/$customDirFromGradle/$className.testCapture.png")
      checkRecordedFileNotExists("$$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun unitTestWhenRunTwice() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      unitTest()
      val output = unitTest().output
      assertSkipped(output)

      checkResultsSummaryFileNotExists()
      checkRecordedFileNotExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun recordWithPropertiesAfterUnitTest() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      unitTest()
      // Record task shouldn't be skipped even after unit test
      val recordWithSystemParameter = recordWithSystemParameter()
      assertNotSkipped(recordWithSystemParameter.output)

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun recordAfterUnitTest() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      unitTest()
      // Record task shouldn't be skipped even after unit test
      record()

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun canRecordWhenRemoveOutputDirBeforeTests() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      buildGradle.removeOutputDirBeforeTestTypeTask = true
      record()
    }
  }

  @Test
  fun verify_changeDetect() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      record()
      changeScreen()
      val recordFileHash1 = getFileHash("$screenshotAndName.testCapture.png")

      verifyAndFail().shouldDetectChangedPngCapture()

      val recordFileHash2 = getFileHash("$screenshotAndName.testCapture.png")
      assert(recordFileHash1 == recordFileHash2)
      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_actual.png")
      checkResultCount(changed = 1)
    }
  }

  @Test
  fun checkIfOutputIsUsed() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      record()
      changeScreen()
      compare()
      removeRoborazziOutputDir()
      record()
      compare()

      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkResultCount(unchanged = 1)
    }
  }

  /**
   * This test is for the issue
   * https://github.com/takahirom/roborazzi/issues/261
   */
  @Test
  fun checkRevertCache() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      record()
      changeScreen()
      compare()
      resetScreen()
      clean()
      record()
      changeScreen()
      compare()

      checkResultsSummaryFileExists()
//      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_actual.png")
      checkResultCount(changed = 1)
    }
  }

  @Test
  fun verify_addDetect() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      record()
      addTest()

      verifyAndFail().shouldDetectNonExistentPngCapture()

      checkResultsSummaryFileExists()
      checkRecordedFileNotExists("$addedScreenshotAndName.testCapture.png")
      checkRecordedFileExists("$addedScreenshotAndName.testCapture_compare.png")
      checkRecordedFileExists("$addedScreenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun recordWithCompareParameter() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      recordWithCompareParameter()

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun recordWithSmallProperty() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      record()
      changeScreen()
      val recordFileHash1 = getFileHash("$screenshotAndName.testCapture.png")

      recordWithScaleSize()

      val recordFileHash2 = getFileHash("$screenshotAndName.testCapture.png")
      assert(recordFileHash1 != recordFileHash2)
      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun record_noTestFiles() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      removeTests()
      record()

      // Summary file will be generated even if no test files
      checkResultsSummaryFileExists()
      // Test will be skipped when no source so no output
      checkResultFileNotExists(resultFileSuffix)
    }
  }

  @Test
  fun record_noTests() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      removeTests()
      addTestClass()
      record()

      checkResultsSummaryFileExists()
      checkResultFileNotExists(resultFileSuffix)
    }
  }

  @Test
  fun verify_nochange_with_changed_build_dir() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      val buildDirName = "testCustomBuildDirName"
      changeBuildDir(buildDirName)
      defaultBuildDir = buildDirName
      record()
      verify()

      checkResultsSummaryFileExists()
      checkResultFileExists(resultFileSuffix)
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
      checkResultCount(unchanged = 1)
    }
  }

  @Test
  fun verifyAndRecord_changeDetect() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      record()
      val recordFileHash1 = getFileHash("$screenshotAndName.testCapture.png")
      changeScreen()

      verifyAndRecordAndFail()

      val recordFileHash2 = getFileHash("$screenshotAndName.testCapture.png")
      assert(recordFileHash1 != recordFileHash2)
      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkResultFileExists(resultFileSuffix)
      checkRecordedFileExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun verifyAndRecord_nochange() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      record()
      verifyAndRecord()

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun compare() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      record()
      changeScreen()
      compare()

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkResultFileExists(resultFileSuffix)
      checkRecordedFileExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun compareWithSystemParameter() {
    println("start compareWithSystemParameter")
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      record()
      changeScreen()
      compareWithSystemParameter()

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun compare_nochange() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      record()
      compare()

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun compareWithCustomPath() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      removeTests()
      addTestCaptureWithCustomPathTest()
      record()
      changeScreen()
      compare()

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$customReferenceScreenshotAndName.png")
      checkRecordedFileExists("${customCompareScreenshotAndName}_compare.png")
      checkRecordedFileExists("${customCompareScreenshotAndName}_actual.png")
      checkRecordedFileExists("app/build/outputs/roborazzi/custom_outputDirectoryPath_from_rule/custom_outputFileProvider-com.github.takahirom.integration_test_project.RoborazziTest.testCaptureWithCustomPath.png")
    }
  }

  @Test
  fun compareWithCustomPathAndCaptureFilePathStrategy() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      removeTests()
      addTestCaptureWithCustomPathTest()
      addRelativeFromContextRecordFilePathStrategyGradleProperty()
      record()
      changeScreen()
      compare()

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$customReferenceScreenshotAndNameWithRoborazziContext.png")
      checkRecordedFileExists("${customCompareScreenshotAndNameWithRoborazziContext}_compare.png")
      checkRecordedFileExists("${customCompareScreenshotAndNameWithRoborazziContext}_actual.png")
      checkRecordedFileExists("app/build/outputs/roborazzi/custom_outputDirectoryPath_from_rule/custom_outputFileProvider-com.github.takahirom.integration_test_project.RoborazziTest.testCaptureWithCustomPath.png")
    }
  }

  @Test
  fun secondImagesIsSkippedIfFirstVerificationFails() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      removeTests()
      addRuleTest()
      record()
      changeScreen()

      verifyAndFail().shouldDetectChangedPngCapture()

      checkResultsSummaryFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_2.png")
      // If the first verification fails, the second verification will be skipped.
      checkRecordedFileNotExists("$screenshotAndName.testCapture_2_actual.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_2_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_2_actual.png")
    }
  }

  @Test
  fun shouldNotRetainPreviousTestResults() {
    RoborazziGradleRootProject(testProjectDir).appModule.apply {
      removeTests()
      addMultipleTest()

      recordWithFilter1()
      recordWithFilter2()

      checkResultCount(recorded = 1)
    }
  }
}