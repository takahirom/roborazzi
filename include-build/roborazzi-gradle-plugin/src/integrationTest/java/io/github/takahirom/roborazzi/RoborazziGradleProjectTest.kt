package io.github.takahirom.roborazzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RoborazziGradleProjectTest {

  @get:Rule
  val testProjectDir = TemporaryFolder()

  private val screenshotAndName =
    "app/build/outputs/roborazzi/com.github.takahirom.integration_test_project.RoborazziTest"

  @Test
  fun record() {
    RoborazziGradleProject(testProjectDir).apply {
      record()

      checkCompareFileNotExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun recordWithPropertiesAfterUnitTest() {
    RoborazziGradleProject(testProjectDir).apply {
      unitTest()
      // Record task shouldn't be skipped even after unit test
      recordWithSystemParameter()

      checkCompareFileNotExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun recordAfterUnitTest() {
    RoborazziGradleProject(testProjectDir).apply {
      unitTest()
      // Record task shouldn't be skipped even after unit test
      record()

      checkCompareFileNotExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun verify_changeDetect() {
    RoborazziGradleProject(testProjectDir).apply {
      record()
      changeScreen()
      val recordFileHash1 = getFileHash("$screenshotAndName.testCapture.png")

      verifyAndFail()

      val recordFileHash2 = getFileHash("$screenshotAndName.testCapture.png")
      assertEquals(recordFileHash1, recordFileHash2)
      checkCompareFileNotExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_actual.png")
    }
  }


  @Test
  fun verify_nochange() {
    RoborazziGradleProject(testProjectDir).apply {
      record()
      verify()

      checkCompareFileNotExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }


  @Test
  fun verifyAndRecord_changeDetect() {
    RoborazziGradleProject(testProjectDir).apply {
      record()
      val recordFileHash1 = getFileHash("$screenshotAndName.testCapture.png")
      changeScreen()

      verifyAndRecordAndFail()

      val recordFileHash2 = getFileHash("$screenshotAndName.testCapture.png")
      assertNotEquals(recordFileHash1, recordFileHash2)
      checkCompareFileNotExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }


  @Test
  fun verifyAndRecord_nochange() {
    RoborazziGradleProject(testProjectDir).apply {
      record()
      verifyAndRecord()

      checkCompareFileNotExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun compare() {
    RoborazziGradleProject(testProjectDir).apply {
      record()
      changeScreen()
      compare()

      checkCompareFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileExists("$screenshotAndName.testCapture_actual.png")
    }
  }

  @Test
  fun compare_nochange() {
    RoborazziGradleProject(testProjectDir).apply {
      record()
      compare()

      checkCompareFileExists()
      checkRecordedFileExists("$screenshotAndName.testCapture.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$screenshotAndName.testCapture_actual.png")
    }
  }
}