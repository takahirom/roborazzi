package com.github.takahirom.roborazzi

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.annotations.ManualClockOptions
import org.junit.Assert.assertEquals
import org.junit.Test
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

@OptIn(ExperimentalRoborazziApi::class)
class ShardOfDesktopPreviewsTest {

  private val phone = "spec:width=411dp,height=891dp,dpi=420"
  private val tablet = "spec:width=800dp,height=1280dp,dpi=240"

  private fun parameter(
    name: String,
    previewInfo: AndroidPreviewInfo = AndroidPreviewInfo(),
    manualClockOptions: ManualClockOptions? = null,
  ) = DesktopPreviewTestParameter(
    preview = fakePreview(name, previewInfo),
    manualClockOptions = manualClockOptions,
  )

  private fun fakePreview(name: String, info: AndroidPreviewInfo) =
    object : ComposablePreview<AndroidPreviewInfo> {
      override val previewInfo = info
      override val previewIndex: Int? = null
      override val previewIndexDisplayName: String? = null
      override val otherAnnotationsInfo = null
      override val declaringClass = ShardOfDesktopPreviewsTest::class.java.name
      override val methodName = name
      override val methodParametersType = ""

      @Composable
      override fun invoke() = Unit

      override fun toString() = name
    }

  private fun shard(
    parameters: List<DesktopPreviewTestParameter>,
    shardIndex: Int?,
    totalShards: Int,
    profile: DesktopPreviewRenderProfile = DesktopPreviewRenderProfile.AndroidCompatible,
  ): List<String> =
    shardOfDesktopPreviews(parameters, profile, shardIndex, totalShards)
      .map { it.preview.methodName }

  @Test
  fun `a single class gets every preview, sorted`() {
    val parameters = listOf(parameter("c"), parameter("a"), parameter("b"))

    assertEquals(listOf("a", "b", "c"), shard(parameters, shardIndex = null, totalShards = 1))
  }

  @Test
  fun `previews that share a scene stay next to each other`() {
    val parameters = listOf(
      parameter("a", AndroidPreviewInfo(device = phone)),
      parameter("b", AndroidPreviewInfo(device = tablet)),
      parameter("c", AndroidPreviewInfo(device = phone)),
      parameter("d", AndroidPreviewInfo(device = tablet)),
    )

    // Sorted by surface first, so the two phone previews and the two tablet previews are adjacent
    // and can each be captured in one scene.
    assertEquals(
      listOf("a", "c", "b", "d"),
      shard(parameters, shardIndex = null, totalShards = 1),
    )
  }

  @Test
  fun `shards are contiguous slices, not every nth preview`() {
    val parameters = ('a'..'h').map { parameter(it.toString()) }

    // Round-robin would give shard 0 a, c, e, g - four previews from one group, but interleaved
    // with the other shard's, which is exactly what stops a group from being captured together.
    assertEquals(listOf("a", "b", "c", "d"), shard(parameters, shardIndex = 0, totalShards = 2))
    assertEquals(listOf("e", "f", "g", "h"), shard(parameters, shardIndex = 1, totalShards = 2))
  }

  @Test
  fun `every preview lands in exactly one shard when the split is uneven`() {
    val parameters = ('a'..'g').map { parameter(it.toString()) }
    val totalShards = 3

    val shards = (0 until totalShards).map { shard(parameters, it, totalShards) }

    // 7 previews over 3 classes: the remainder goes to the first shards, so no class is more than
    // one preview larger than another.
    assertEquals(listOf(3, 2, 2), shards.map { it.size })
    assertEquals(('a'..'g').map { it.toString() }, shards.flatten())
  }

  @Test
  fun `more shards than previews leaves the extra classes empty rather than failing`() {
    val parameters = listOf(parameter("a"), parameter("b"))

    assertEquals(listOf("a"), shard(parameters, shardIndex = 0, totalShards = 4))
    assertEquals(listOf("b"), shard(parameters, shardIndex = 1, totalShards = 4))
    assertEquals(emptyList<String>(), shard(parameters, shardIndex = 2, totalShards = 4))
    assertEquals(emptyList<String>(), shard(parameters, shardIndex = 3, totalShards = 4))
  }

  @Test
  fun `previews needing their own scene sort after the ones that can share`() {
    val parameters = listOf(
      parameter("manual", manualClockOptions = ManualClockOptions(advanceTimeMillis = 0L)),
      parameter("plain"),
    )

    // "manual" sorts first by name, but last by reusability, so the shareable previews are not
    // split apart by a preview that cannot join them.
    assertEquals(listOf("plain", "manual"), shard(parameters, shardIndex = null, totalShards = 1))
  }

  @Test
  fun `the order does not depend on the order the scanner found the previews in`() {
    val names = ('a'..'f').map { it.toString() }
    val forward = names.map { parameter(it) }
    val backward = forward.reversed()

    assertEquals(
      shard(forward, shardIndex = 1, totalShards = 3),
      shard(backward, shardIndex = 1, totalShards = 3),
    )
  }
}
