package io.github.takahirom.roborazzi

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RoborazziGradleProjectTest {

  @get:Rule
  val testProjectDir = TemporaryFolder()
  private val pathAndName =
    "app/build/outputs/roborazzi/com.github.takahirom.integration_test_project.RoborazziTest"

  @Test
  fun record() {
    RoborazziGradleProject(testProjectDir).apply {
      record()

      checkRecordedFileExists("$pathAndName.testCapture.png")
      checkRecordedFileNotExists("$pathAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$pathAndName.testCapture_actual.png")
    }
  }

  @Test
  fun recordWithPropertiesAfterUnitTest() {
    RoborazziGradleProject(testProjectDir).apply {
      unitTest()
      // Record task shouldn't be skipped even after unit test
      recordWithSystemParameter()

      checkRecordedFileExists("$pathAndName.testCapture.png")
      checkRecordedFileNotExists("$pathAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$pathAndName.testCapture_actual.png")
    }
  }

  @Test
  fun recordAfterUnitTest() {
    RoborazziGradleProject(testProjectDir).apply {
      unitTest()
      // Record task shouldn't be skipped even after unit test
      record()

      checkRecordedFileExists("$pathAndName.testCapture.png")
      checkRecordedFileNotExists("$pathAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$pathAndName.testCapture_actual.png")
    }
  }

  @Test
  fun verify_changeDetect() {
    RoborazziGradleProject(testProjectDir).apply {
      record()
      changeScreen()
      verifyAndFail()

      checkRecordedFileExists("$pathAndName.testCapture.png")
      checkRecordedFileExists("$pathAndName.testCapture_compare.png")
      checkRecordedFileExists("$pathAndName.testCapture_actual.png")
    }
  }


  @Test
  fun verify_nochange() {
    RoborazziGradleProject(testProjectDir).apply {
      record()
      verify()

      checkRecordedFileExists("$pathAndName.testCapture.png")
      checkRecordedFileNotExists("$pathAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$pathAndName.testCapture_actual.png")
    }
  }

  @Test
  fun compare() {
    RoborazziGradleProject(testProjectDir).apply {
      record()
      changeScreen()
      compare()

      checkRecordedFileExists("$pathAndName.testCapture.png")
      checkRecordedFileExists("$pathAndName.testCapture_compare.png")
      checkRecordedFileExists("$pathAndName.testCapture_actual.png")
    }
  }

  @Test
  fun compare_nochange() {
    RoborazziGradleProject(testProjectDir).apply {
      record()
      compare()

      checkRecordedFileExists("$pathAndName.testCapture.png")
      checkRecordedFileNotExists("$pathAndName.testCapture_compare.png")
      checkRecordedFileNotExists("$pathAndName.testCapture_actual.png")
    }
  }
}