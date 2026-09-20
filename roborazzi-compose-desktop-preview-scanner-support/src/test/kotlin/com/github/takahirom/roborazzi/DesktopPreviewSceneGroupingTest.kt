package com.github.takahirom.roborazzi

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.annotations.ManualClockOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

@OptIn(ExperimentalRoborazziApi::class)
class DesktopPreviewSceneGroupingTest {

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
      override val declaringClass = DesktopPreviewSceneGroupingTest::class.java.name
      override val methodName = name
      override val methodParametersType = ""

      @Composable
      override fun invoke() = Unit

      override fun toString() = name
    }

  private fun group(
    parameters: List<DesktopPreviewTestParameter>,
    profile: DesktopPreviewRenderProfile = DesktopPreviewRenderProfile.AndroidCompatible,
  ): List<List<String>> =
    groupDesktopPreviewsByScene(parameters, profile)
      .map { group -> group.map { it.preview.methodName } }

  @Test
  fun `previews that need the same surface share a group`() {
    val groups = group(
      listOf(
        parameter("a"),
        parameter("b"),
        parameter("c"),
      )
    )

    assertEquals(listOf(listOf("a", "b", "c")), groups)
  }

  @Test
  fun `a different surface size is a different group`() {
    val groups = group(
      listOf(
        parameter("phone", AndroidPreviewInfo(device = "spec:width=411dp,height=891dp,dpi=420")),
        parameter("tablet", AndroidPreviewInfo(device = "spec:width=800dp,height=1280dp,dpi=240")),
        parameter("phoneAgain", AndroidPreviewInfo(device = "spec:width=411dp,height=891dp,dpi=420")),
      )
    )

    assertEquals(listOf(listOf("phone", "phoneAgain"), listOf("tablet")), groups)
  }

  @Test
  fun `options the composition carries do not split a group`() {
    // fontScale, the night bit and the background are provided to the content, not to the scene,
    // so previews that differ only in these still share one scene. widthDp/heightDp are not among
    // them: they resize the window, the same way the Robolectric qualifiers do, so a sized preview
    // needs a scene of its own.
    val groups = group(
      listOf(
        parameter("plain"),
        parameter("large", AndroidPreviewInfo(fontScale = 2f)),
        parameter("night", AndroidPreviewInfo(uiMode = 0x20)),
        parameter("sized", AndroidPreviewInfo(widthDp = 200, heightDp = 120)),
        parameter("background", AndroidPreviewInfo(showBackground = true)),
      )
    )

    assertEquals(listOf(listOf("plain", "large", "night", "background"), listOf("sized")), groups)
  }

  @Test
  fun `a different locale is a different group because it is set outside the scene`() {
    val groups = group(
      listOf(
        parameter("default"),
        parameter("japanese", AndroidPreviewInfo(locale = "ja")),
      )
    )

    assertEquals(listOf(listOf("default"), listOf("japanese")), groups)
  }

  @Test
  fun `a manual clock preview is alone in its group`() {
    // The scene clock cannot be rewound, so a preview whose frame is chosen by hand cannot follow
    // another preview in the same scene - not even another manual clock preview.
    val groups = group(
      listOf(
        parameter("plain"),
        parameter("atZero", manualClockOptions = ManualClockOptions(advanceTimeMillis = 0L)),
        parameter("atFiveHundred", manualClockOptions = ManualClockOptions(advanceTimeMillis = 500L)),
        parameter("plainAgain"),
      )
    )

    // The two plain previews still share their scene; only the manual clock ones stand alone.
    assertEquals(
      listOf(listOf("plain", "plainAgain"), listOf("atZero"), listOf("atFiveHundred")),
      groups,
    )
  }

  @Test
  fun `the given order is preserved so that JVM global state sees the same sequence`() {
    val groups = group(
      listOf(
        parameter("first"),
        parameter("tablet", AndroidPreviewInfo(device = "spec:width=800dp,height=1280dp,dpi=240")),
        parameter("second"),
      )
    )

    assertEquals(listOf(listOf("first", "second"), listOf("tablet")), groups)
  }

  @Test
  fun `the default profile puts everything on one surface`() {
    // The Desktop profile ignores the device, so previews that the AndroidCompatible profile would
    // separate all land on the same 1024x768 scene.
    val groups = group(
      listOf(
        parameter("phone", AndroidPreviewInfo(device = "spec:width=411dp,height=891dp,dpi=420")),
        parameter("tablet", AndroidPreviewInfo(device = "spec:width=800dp,height=1280dp,dpi=240")),
      ),
      profile = DesktopPreviewRenderProfile.Desktop,
    )

    assertEquals(listOf(listOf("phone", "tablet")), groups)
  }

  @Test
  fun `the scene key says whether the group can be shared`() {
    val plain = desktopPreviewSceneKey(parameter("plain"), DesktopPreviewRenderProfile.Desktop)
    val manual = desktopPreviewSceneKey(
      parameter("manual", manualClockOptions = ManualClockOptions(advanceTimeMillis = 500L)),
      DesktopPreviewRenderProfile.Desktop,
    )

    assertTrue(plain.reusable)
    assertFalse(manual.reusable)
    assertEquals(1024, plain.surfaceWidth)
    assertEquals(768, plain.surfaceHeight)
  }

  @Test
  fun `an empty list produces no groups`() {
    assertEquals(emptyList<List<String>>(), group(emptyList()))
  }
}
