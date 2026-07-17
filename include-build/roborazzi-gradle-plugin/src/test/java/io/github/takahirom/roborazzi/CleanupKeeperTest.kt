package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.CaptureResult
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.ROBORAZZI_ANNOTATED_FILE_PATH_KEY
import com.github.takahirom.roborazzi.ROBORAZZI_UI_TREE_FILE_PATH_KEY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit coverage for [keeperFilesFor], the cleanup keeper-set derivation.
 *
 * The keeper set must contain the UI tree sidecar / annotated image THIS run
 * actually wrote (recorded in the result's context data), not paths synthesized
 * from the image basenames. This covers two bugs:
 *  - an unchanged verify's sidecar/annotated files use the `_actual` basename that
 *    no image file in the result carries, so synthesizing from basenames deleted
 *    the fresh files; and
 *  - synthesizing unconditionally kept a stale sidecar/annotated file even when the
 *    dump/annotate feature was off and nothing was written this run.
 */
@OptIn(ExperimentalRoborazziApi::class)
class CleanupKeeperTest {
  private fun abs(path: String): String = File(path).absolutePath

  @Test
  fun unchangedVerifyKeepsTheActualSidecarAndAnnotated() {
    val sidecar = abs("/out/compare/MyTest_actual.uitree.json")
    val annotated = abs("/out/compare/MyTest_actual.annotated.png")
    val result = CaptureResult.Unchanged(
      goldenFile = abs("/out/MyTest.png"),
      timestampNs = 0L,
      contextData = mapOf(
        ROBORAZZI_UI_TREE_FILE_PATH_KEY to sidecar,
        ROBORAZZI_ANNOTATED_FILE_PATH_KEY to annotated,
      ),
    )

    val keepers = keeperFilesFor(result)

    assertTrue("golden must be kept", keepers.contains(abs("/out/MyTest.png")))
    assertTrue("the _actual sidecar must be kept", keepers.contains(sidecar))
    assertTrue("the _actual annotated image must be kept", keepers.contains(annotated))
  }

  @Test
  fun featureOffDoesNotSynthesizeSidecarKeepers() {
    // No UI tree context data: the feature was off this run, so no sidecar/annotated
    // file was written and none must be synthesized as a keeper (otherwise a stale
    // one would survive cleanup).
    val result = CaptureResult.Recorded(
      goldenFile = abs("/out/MyTest.png"),
      timestampNs = 0L,
      contextData = emptyMap(),
    )

    val keepers = keeperFilesFor(result)

    assertEquals(setOf(abs("/out/MyTest.png")), keepers)
    assertFalse(
      "a stale sidecar must not be synthesized as a keeper",
      keepers.contains(abs("/out/MyTest.uitree.json")),
    )
    assertFalse(
      "a stale annotated image must not be synthesized as a keeper",
      keepers.contains(abs("/out/MyTest.annotated.png")),
    )
  }

  @Test
  fun recordKeepsGoldenImagesAndItsSidecar() {
    val sidecar = abs("/out/MyTest.uitree.json")
    val result = CaptureResult.Recorded(
      goldenFile = abs("/out/MyTest.png"),
      timestampNs = 0L,
      contextData = mapOf(ROBORAZZI_UI_TREE_FILE_PATH_KEY to sidecar),
    )

    val keepers = keeperFilesFor(result)

    assertEquals(setOf(abs("/out/MyTest.png"), sidecar), keepers)
  }
}
