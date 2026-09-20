package com.github.takahirom.roborazzi

import org.junit.Assert.assertEquals
import org.junit.Test
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo

/**
 * The expected numbers here are not derived from this implementation: they are the image sizes the
 * Robolectric runtime actually produced for the same previews in the cross-runtime sample. The
 * point of the whole render profile is that the two runtimes agree, so the goldens have to come
 * from the other runtime.
 */
@OptIn(ExperimentalRoborazziApi::class)
class DesktopPreviewRenderSpecTest {

  private fun resolve(
    previewInfo: AndroidPreviewInfo,
    profile: DesktopPreviewRenderProfile = DesktopPreviewRenderProfile.AndroidCompatible,
  ) = DesktopPreviewRenderSpec.resolve(previewInfo, profile)

  @Test
  fun `the default profile keeps the historical 1024x768 surface at density 1`() {
    val spec = resolve(AndroidPreviewInfo(), DesktopPreviewRenderProfile.Desktop)

    assertEquals(DesktopPreviewRenderSpec(1024, 768, 1f), spec)
  }

  @Test
  fun `the default profile ignores the device even when the preview declares one`() {
    val spec = resolve(
      AndroidPreviewInfo(device = "spec:width=411dp,height=891dp,dpi=420"),
      DesktopPreviewRenderProfile.Desktop,
    )

    assertEquals(DesktopPreviewRenderSpec(1024, 768, 1f), spec)
  }

  @Test
  fun `the default profile sizes in raw pixels because a dp is a pixel there`() {
    val spec = resolve(
      AndroidPreviewInfo(widthDp = 2000, heightDp = 120),
      DesktopPreviewRenderProfile.Desktop,
    )

    assertEquals(DesktopPreviewRenderSpec(2000, 768, 1f), spec)
  }

  @Test
  fun `a preview with no device falls back to the profile's default device`() {
    // id:pixel_4a is 1080x2340 px at 440dpi, which is 392x850 dp, which is 1078x2337 px.
    val spec = resolve(AndroidPreviewInfo())

    assertEquals(1078, spec.surfaceWidth)
    assertEquals(2337, spec.surfaceHeight)
    assertEquals(2.75f, spec.density)
  }

  @Test
  fun `a device declared in pixels loses the fraction the Robolectric qualifier loses`() {
    // Rounding the raw 1080px instead of going through dp would give 1080, and every device-less
    // preview would then be 2px wider on desktop than on Robolectric.
    assertEquals(1078, resolve(AndroidPreviewInfo(device = "id:pixel_4a")).surfaceWidth)
  }

  @Test
  fun `a device spec in dp is floored, not ceiled`() {
    // 411 * 2.625 = 1078.875. ComposablePreviewScanner's own Dimensions.inPx would ceil this to
    // 1079; the Robolectric runtime produces 1078.
    val spec = resolve(AndroidPreviewInfo(device = "spec:width=411dp,height=891dp,dpi=420"))

    assertEquals(1078, spec.surfaceWidth)
    assertEquals(2.625f, spec.density)
  }

  @Test
  fun `a fractional device width is floored`() {
    // 201 * 2.75 = 552.75, measured as 552 on Robolectric.
    val spec = resolve(AndroidPreviewInfo(device = "spec:width=201dp,height=400dp,dpi=440"))

    assertEquals(552, spec.surfaceWidth)
    assertEquals(1100, spec.surfaceHeight)
  }

  @Test
  fun `a whole-number device size needs no rounding at all`() {
    val spec = resolve(AndroidPreviewInfo(device = "spec:width=800dp,height=1280dp,dpi=240"))

    assertEquals(DesktopPreviewRenderSpec(1200, 1920, 1.5f), spec)
  }

  @Test
  fun `the preview's own device wins over the profile's default`() {
    val spec = resolve(AndroidPreviewInfo(device = "spec:width=800dp,height=1280dp,dpi=240"))

    assertEquals(1200, spec.surfaceWidth)
  }

  @Test
  fun `widthDp and heightDp resize the window the way the qualifiers do`() {
    // Measured: the Robolectric runtime renders @Preview(widthDp = 200, heightDp = 120) on the
    // Pixel 4a in a 550x330 window, not in the device's own.
    val spec = resolve(AndroidPreviewInfo(widthDp = 200, heightDp = 120))

    assertEquals(550, spec.surfaceWidth)
    assertEquals(330, spec.surfaceHeight)
  }

  @Test
  fun `only the axis the preview sets is replaced`() {
    // Measured: @Preview(heightDp = 500) renders in a 1080x1375 window on the Robolectric runtime,
    // so the unset axis keeps the device's size. The width differs by the dp round trip the
    // device-less path makes on both runtimes, which is what 1078 is.
    val spec = resolve(AndroidPreviewInfo(heightDp = 500))

    assertEquals(1078, spec.surfaceWidth)
    assertEquals(1375, spec.surfaceHeight)
  }

  @Test
  fun `a preview larger than the device enlarges the surface`() {
    val spec = resolve(AndroidPreviewInfo(widthDp = 1000, heightDp = 1000))

    assertEquals(2750, spec.surfaceWidth)
    assertEquals(2750, spec.surfaceHeight)
  }

  @Test
  fun `an unparseable device is rejected instead of silently falling back`() {
    val failure = runCatching {
      resolve(AndroidPreviewInfo(device = "pixel_4a"))
    }.exceptionOrNull()

    assertEquals(IllegalArgumentException::class.java, failure?.javaClass)
    assert(failure!!.message!!.contains("pixel_4a")) { "Unexpected message: ${failure.message}" }
  }
}
