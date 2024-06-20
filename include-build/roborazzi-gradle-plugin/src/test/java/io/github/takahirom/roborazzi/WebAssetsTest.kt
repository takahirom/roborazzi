package io.github.takahirom.roborazzi

import junit.framework.TestCase.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test

class WebAssetsTest {

  @get:Rule
  val tmpDir: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

  @Test
  fun testWritesWebAssetsSuccessfullyToReportsDirectory() {
    val reportDir = tmpDir.newFolder("roborazzi-reports")
    val cssFile = File(reportDir, "assets/report-style.css")
    val materializeCssFile = File(reportDir, "assets/materialize.min.css")
    val materializeJsFile = File(reportDir, "assets/materialize.min.js")
    val materialIconsCssFile = File(reportDir, "assets/material-icons.css")
    val materialIconsTtfFile = File(reportDir, "assets/MaterialIcons-Regular.ttf")

    WebAssets.create().writeToRoborazziReportsDir(reportDir)

    assertTrue(cssFile.exists())
    assertTrue(materializeCssFile.exists())
    assertTrue(materializeJsFile.exists())
    assertTrue(materialIconsCssFile.exists())
    assertTrue(materialIconsTtfFile.exists())
  }
}