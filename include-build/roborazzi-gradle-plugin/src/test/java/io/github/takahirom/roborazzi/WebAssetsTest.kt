package io.github.takahirom.roborazzi

import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test

class WebAssetsTest {

  @get:Rule
  val tmpDir: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

  private lateinit var sut: WebAssets

  @Before
  fun setUp() {
    sut = WebAssets.create()
  }

  @Test
  fun testWritesWebAssetsSuccessfullyToReportsDirectory() {
    val reportDir = tmpDir.newFolder("roborazzi-reports")
    val assetFiles = sut.assets.map { File(reportDir, "assets/$it") }

   sut.writeToRoborazziReportsDir(reportDir)

    assetFiles.forEach { file->
      assertTrue(file.exists())
    }
  }
}