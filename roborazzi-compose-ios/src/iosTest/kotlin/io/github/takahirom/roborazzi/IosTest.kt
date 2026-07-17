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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.UIImageRoboCanvas
import com.github.takahirom.roborazzi.UiTreeDumpOptions
import com.github.takahirom.roborazzi.roborazziSystemPropertyProjectPath
import com.github.takahirom.roborazzi.roborazziSystemPropertyResultDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.isEqualToData
import platform.Foundation.stringWithContentsOfFile
import platform.UIKit.UIImage

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

  /**
   * Forces a changed comparison so the default [RoborazziOptions.CompareOptions]
   * grid style produces a `_compare` image, then checks it was rendered through
   * the grid path (three sections plus 16dp margins), which only happens when a
   * positive density is resolved from the captured node at runtime.
   */
  @OptIn(ExperimentalTestApi::class, ExperimentalForeignApi::class)
  @Test
  fun gridComparisonImageIsWiderThanThreeSections() {
    val baseDir = resolveAbsolutePath(
      NSTemporaryDirectory() + "roborazzi-ios-grid-${NSProcessInfo.processInfo.systemUptime}"
    ).trimEnd('/')
    val goldenPath = "$baseDir/ios_grid.png"
    val compareDir = "$baseDir/compare"

    runComposeUiTest {
      setContent {
        MaterialTheme {
          Box(modifier = Modifier.background(Color.Red).size(50.dp))
        }
      }
      onRoot().captureRoboImage(
        composeUiTest = this,
        filePath = goldenPath,
        roborazziOptions = options(RoborazziTaskType.Record, compareDir),
      )
    }

    runComposeUiTest {
      setContent {
        MaterialTheme {
          // Different color -> the comparison is "changed" and a grid
          // _compare image is written.
          Box(modifier = Modifier.background(Color.Blue).size(50.dp))
        }
      }
      onRoot().captureRoboImage(
        composeUiTest = this,
        filePath = goldenPath,
        roborazziOptions = options(RoborazziTaskType.Compare, compareDir),
      )
    }

    val comparePath = "$compareDir/ios_grid_compare.png"
    assertTrue(
      NSFileManager.defaultManager.fileExistsAtPath(comparePath),
      "A _compare image should be written for a changed comparison at $comparePath",
    )
    val golden = UIImageRoboCanvas.fromFile(goldenPath)
    val compare = UIImageRoboCanvas.fromFile(comparePath)
    assertTrue(golden != null && compare != null, "golden and compare images should load")
    // Grid = 3 sections + 2 * 16dp margin, so it is strictly wider than 3
    // sections; the Simple fallback (no margin) would be exactly 3 sections.
    assertTrue(
      compare.width > golden.width * 3,
      "grid comparison (width=${compare.width}) should exceed 3x golden width (${golden.width * 3})",
    )
    golden.release()
    compare.release()
  }

  /**
   * With [UiTreeDumpOptions] enabled, the iOS capture path writes a
   * `.uitree.json` sidecar AND (by default) an annotated Set-of-Mark
   * `.annotated.png` next to the golden image. Asserts the sidecar carries a
   * grep-able testTag line, and that the annotated PNG exists, has the same pixel
   * dimensions as the screenshot, and differs from it (boxes drawn).
   */
  @OptIn(ExperimentalTestApi::class, ExperimentalForeignApi::class)
  @Test
  fun uiTreeSidecarAndAnnotatedImageAreWritten() {
    val baseDir = resolveAbsolutePath(
      NSTemporaryDirectory() + "roborazzi-ios-uitree-${NSProcessInfo.processInfo.systemUptime}"
    ).trimEnd('/')
    val goldenPath = "$baseDir/ios_uitree.png"
    val sidecarPath = "$baseDir/ios_uitree.uitree.json"
    val annotatedPath = "$baseDir/ios_uitree.annotated.png"

    runComposeUiTest {
      setContent {
        MaterialTheme {
          Button(
            modifier = Modifier.testTag("button"),
            onClick = { /*TODO*/ },
          ) {
            Text("Hello UI tree")
          }
        }
      }
      onRoot().captureRoboImage(
        composeUiTest = this,
        filePath = goldenPath,
        roborazziOptions = RoborazziOptions(
          taskType = RoborazziTaskType.Record,
          compareOptions = RoborazziOptions.CompareOptions(
            outputDirectoryPath = "$baseDir/compare",
          ),
          uiTreeDumpOptions = UiTreeDumpOptions(),
        ),
      )
    }

    assertTrue(
      NSFileManager.defaultManager.fileExistsAtPath(sidecarPath),
      "UI tree sidecar should be written at $sidecarPath",
    )
    val json = NSString.stringWithContentsOfFile(
      sidecarPath,
      encoding = NSUTF8StringEncoding,
      error = null,
    )
    assertTrue(json != null, "sidecar should be readable")
    assertTrue(
      json.contains("\"schemaVersion\": 1"),
      "sidecar should be valid UI tree JSON",
    )
    assertTrue(
      json.contains("\"testTag\": \"button\""),
      "sidecar should contain the tagged node's testTag",
    )

    assertTrue(
      NSFileManager.defaultManager.fileExistsAtPath(annotatedPath),
      "Annotated Set-of-Mark image should be written at $annotatedPath",
    )
    val goldenSize = pngPixelSize(goldenPath)
    val annotatedSize = pngPixelSize(annotatedPath)
    assertTrue(goldenSize != null && annotatedSize != null, "both PNGs should decode")
    assertEquals(
      goldenSize,
      annotatedSize,
      "annotated image should have the same pixel dimensions as the screenshot",
    )
    // The annotated PNG is a copy of the screenshot with boxes drawn on top, so
    // its bytes must differ from the plain screenshot.
    val goldenData = NSData.dataWithContentsOfFile(goldenPath)
    val annotatedData = NSData.dataWithContentsOfFile(annotatedPath)
    assertTrue(goldenData != null && annotatedData != null, "both PNGs should load as data")
    assertFalse(
      annotatedData.isEqualToData(goldenData),
      "annotated image should differ from the screenshot (boxes drawn)",
    )
  }

  /**
   * With `annotateImage = false`, only the JSON sidecar is written; no annotated
   * image is produced.
   */
  @OptIn(ExperimentalTestApi::class, ExperimentalForeignApi::class)
  @Test
  fun uiTreeAnnotatedImageIsSkippedWhenOptedOut() {
    val baseDir = resolveAbsolutePath(
      NSTemporaryDirectory() + "roborazzi-ios-uitree-noannotate-${NSProcessInfo.processInfo.systemUptime}"
    ).trimEnd('/')
    val goldenPath = "$baseDir/ios_uitree_noannotate.png"
    val sidecarPath = "$baseDir/ios_uitree_noannotate.uitree.json"
    val annotatedPath = "$baseDir/ios_uitree_noannotate.annotated.png"

    runComposeUiTest {
      setContent {
        MaterialTheme {
          Button(
            modifier = Modifier.testTag("button"),
            onClick = { /*TODO*/ },
          ) {
            Text("Hello UI tree")
          }
        }
      }
      onRoot().captureRoboImage(
        composeUiTest = this,
        filePath = goldenPath,
        roborazziOptions = RoborazziOptions(
          taskType = RoborazziTaskType.Record,
          compareOptions = RoborazziOptions.CompareOptions(
            outputDirectoryPath = "$baseDir/compare",
          ),
          uiTreeDumpOptions = UiTreeDumpOptions(annotateImage = false),
        ),
      )
    }

    assertTrue(
      NSFileManager.defaultManager.fileExistsAtPath(sidecarPath),
      "sidecar should still be written when annotateImage=false",
    )
    assertFalse(
      NSFileManager.defaultManager.fileExistsAtPath(annotatedPath),
      "no annotated image should be written when annotateImage=false",
    )
  }
}

/**
 * Decodes a PNG at [path] and returns its (width, height) in pixels, or null when
 * it can't be decoded.
 */
@OptIn(ExperimentalForeignApi::class)
private fun pngPixelSize(path: String): Pair<Int, Int>? {
  val cgImage = UIImage(contentsOfFile = path).CGImage ?: return null
  return CGImageGetWidth(cgImage).toInt() to CGImageGetHeight(cgImage).toInt()
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
