@file:OptIn(ExperimentalRoborazziApi::class, ExperimentalForeignApi::class)

package io.github.takahirom.roborazzi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.roborazziSystemPropertyProjectPath
import com.github.takahirom.roborazzi.roborazziSystemPropertyResultDirectory
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSTemporaryDirectory

class IosTest {
  @OptIn(ExperimentalTestApi::class)
  private fun ComposeUiTest.setSampleContent() {
    setContent {
      MaterialTheme {
        Row {
          listOf(1.0F, 0.2F, 0.0F).forEach { alpha ->
            Column {
              Button(
                modifier = Modifier.alpha(alpha),
                onClick = { /*TODO*/ }) {
                Text("Hello World5")
              }
              Box(
                modifier = Modifier
                  .background(Color.Red.copy(alpha = alpha), MaterialTheme.shapes.small)
                  .size(100.dp),
              )
              Box(
                modifier = Modifier
                  .background(Color.Green.copy(alpha = alpha), MaterialTheme.shapes.small)
                  .size(100.dp),
              )
              Box(
                modifier = Modifier
                  .background(Color.Yellow.copy(alpha = alpha), MaterialTheme.shapes.small)
                  .size(100.dp),
              )
            }
          }
        }
      }
    }
  }

  private fun options(
    taskType: RoborazziTaskType,
    compareOutputDirectoryPath: String,
  ): RoborazziOptions = RoborazziOptions(
    taskType = taskType,
    compareOptions = RoborazziOptions.CompareOptions(
      outputDirectoryPath = compareOutputDirectoryPath,
    ),
  )

  private fun jsonReportCount(): Int {
    val resultDir = resolveAbsolutePath(roborazziSystemPropertyResultDirectory())
    val contents = NSFileManager.defaultManager.contentsOfDirectoryAtPath(resultDir, null)
      ?: return 0
    return contents.count { (it as? String)?.endsWith(".json") == true }
  }

  /**
   * Drives the shared record -> compare -> verify pipeline end to end and pins
   * the new behavior that the common pipeline writes a report JSON file.
   */
  @OptIn(ExperimentalTestApi::class)
  @Test
  fun recordCompareVerifyThroughCommonPipeline() {
    val baseDir = resolveAbsolutePath(
      NSTemporaryDirectory() + "roborazzi-ios-${NSProcessInfo.processInfo.systemUptime}"
    ).trimEnd('/')
    val goldenPath = "$baseDir/ios_pipeline.png"
    val compareDir = "$baseDir/compare"

    runComposeUiTest {
      setSampleContent()

      val reportsBeforeRecord = jsonReportCount()
      // Record: writes the golden image and a Recorded report.
      onRoot().captureRoboImage(
        composeUiTest = this,
        filePath = goldenPath,
        roborazziOptions = options(RoborazziTaskType.Record, compareDir),
      )
      assertTrue(
        NSFileManager.defaultManager.fileExistsAtPath(goldenPath),
        "Golden image should be recorded at $goldenPath",
      )
      assertTrue(
        jsonReportCount() > reportsBeforeRecord,
        "The common pipeline should write a report JSON on record",
      )

      // Compare: identical content -> Unchanged, another report is written.
      val reportsBeforeCompare = jsonReportCount()
      onRoot().captureRoboImage(
        composeUiTest = this,
        filePath = goldenPath,
        roborazziOptions = options(RoborazziTaskType.Compare, compareDir),
      )
      assertTrue(
        jsonReportCount() > reportsBeforeCompare,
        "The common pipeline should write a report JSON on compare",
      )

      // Verify: identical content -> passes (no AssertionError thrown).
      onRoot().captureRoboImage(
        composeUiTest = this,
        filePath = goldenPath,
        roborazziOptions = options(RoborazziTaskType.Verify, compareDir),
      )
    }
  }
}

// Mirrors the production iOS path resolution: a relative Roborazzi path (e.g. a
// project-relative roborazzi.result.dir) resolves against the absolute
// roborazzi.project.path when set, falling back to the process working
// directory. This keeps the test looking where the common pipeline actually
// writes the report JSON / outputs.
private fun resolveAbsolutePath(path: String): String {
  if (path.startsWith("/")) return path
  val projectPath = roborazziSystemPropertyProjectPath()
  val base = if (projectPath == ".") {
    NSFileManager.defaultManager.currentDirectoryPath
  } else {
    projectPath
  }
  return "$base/$path"
}
