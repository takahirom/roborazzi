package com.github.takahirom.roborazzi

import org.junit.Assert.assertEquals
import org.junit.Test
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo

/**
 * The expected numbers here are not derived from this implementation: they are the image sizes the
 * Robolectric runtime actually produced for the same previews in the cross-runtime sample. The
 * point of the whole device profile is that the two runtimes agree, so the goldens have to come
 * from the other runtime.
 */
@OptIn(ExperimentalRoborazziApi::class)
class DesktopPreviewRenderSpecTest {

  private fun resolve(
    previewInfo: AndroidPreviewInfo,
    profile: DesktopPreviewDeviceProfile = Pixel4aDeviceProfile,
  ) = DesktopPreviewRenderSpec.resolve(previewInfo, profile)

  @Test
  fun `the Desktop profile keeps the historical 1024x768 surface at density 1`() {
    val spec = resolve(AndroidPreviewInfo(), DesktopPreviewDeviceProfile.Desktop)

    assertEquals(DesktopPreviewRenderSpec(1024, 768, 1f), spec)
  }

  @Test
  fun `the Desktop profile ignores the device even when the preview declares one`() {
    val spec = resolve(
      AndroidPreviewInfo(device = "spec:width=411dp,height=891dp,dpi=420"),
      DesktopPreviewDeviceProfile.Desktop,
    )

    assertEquals(DesktopPreviewRenderSpec(1024, 768, 1f), spec)
  }

  @Test
  fun `the Desktop profile sizes in raw pixels because a dp is a pixel there`() {
    val spec = resolve(
      AndroidPreviewInfo(widthDp = 2000, heightDp = 120),
      DesktopPreviewDeviceProfile.Desktop,
    )

    assertEquals(DesktopPreviewRenderSpec(2000, 768, 1f), spec)
  }

  @Test
  fun `a preview with no device falls back to the profile's default device`() {
    // `w393dp` at 440dpi is `floor(393 * 2.75) = 1080`, which is what the Robolectric runtime's
    // Configuration reports for the same qualifiers. Its capture is 1078x2337, two pixels short,
    // because its `Display` is round-tripped while its `Resources` are not; see `screenSizePx`.
    val spec = resolve(AndroidPreviewInfo())

    assertEquals(1080, spec.surfaceWidth)
    assertEquals(2340, spec.surfaceHeight)
    assertEquals(2.75f, spec.density)
  }

  @Test
  fun `the same device sizes the same whether it is the default or the preview's own`() {
    // The profile's default is not a special rounding case. Both spellings floor once, so
    // `w411dp` at 420dpi is 1078px either way.
    val asProfileDefault = DesktopPreviewRenderSpec.resolve(
      AndroidPreviewInfo(),
      Pixel4aDeviceProfile
        .copy(defaultDevice = "spec:width=411dp,height=891dp,dpi=420"),
    )
    val asPreviewDevice = resolve(
      AndroidPreviewInfo(device = "spec:width=411dp,height=891dp,dpi=420"),
    )

    assertEquals(1078, asProfileDefault.surfaceWidth)
    assertEquals(asProfileDefault, asPreviewDevice)
  }

  @Test
  fun `a device declared in pixels is sized at exactly those pixels`() {
    // The scanner's Pixel 4a is a pixel-table entry, 1080x2340 at 440dpi, and those are the
    // pixels rendered rather than the 392x850dp they truncate to. The dp spelling of the same
    // device, `w393dp-h851dp` at 440dpi, lands on the same 1080x2340.
    val viaPixelTable = DesktopPreviewRenderSpec.resolve(
      AndroidPreviewInfo(),
      Pixel4aDeviceProfile.copy(defaultDevice = "id:pixel_4a"),
    )

    assertEquals(DesktopPreviewRenderSpec(1080, 2340, 2.75f), viaPixelTable)
    assertEquals(viaPixelTable, resolve(AndroidPreviewInfo()))
  }

  @Test
  fun `the MediumPhone profile renders the pixels Android Studio previews`() {
    // Studio, and Google's Compose Preview Screenshot Testing, preview Medium Phone at exactly
    // 1080x2400 at 420dpi. Reading those pixels back as 411dp would give 1078x2399.
    val spec = resolve(AndroidPreviewInfo(), DesktopPreviewDeviceProfile.MediumPhone)

    assertEquals(DesktopPreviewRenderSpec(1080, 2400, 2.625f), spec)
  }

  @Test
  fun `a preview naming Medium Phone is sized at the pixels the device declares`() {
    // Both spellings give the device's real 1080x2400 here, whether it arrives as the profile's
    // default or as the preview's own `device`.
    //
    // The Robolectric runtime is 1078x2399 for this one, and the gap is not ours to close:
    // ComposablePreviewScanner turns 1080px into a `w411dp` qualifier, and no integer dp maps
    // back to 1080 at 420dpi (411 -> 1078, 412 -> 1081). Pixel 4a has no such gap because its
    // 1080px is exactly `w393dp` at 440dpi.
    val spec = resolve(
      AndroidPreviewInfo(device = "id:medium_phone"),
      DesktopPreviewDeviceProfile.MediumPhone,
    )

    assertEquals(DesktopPreviewRenderSpec(1080, 2400, 2.625f), spec)
  }

  @Test
  fun `a blank default device is rejected because the parser would take it as a device`() {
    val failure = runCatching {
      Pixel4aDeviceProfile.copy(defaultDevice = "")
    }.exceptionOrNull()

    assertEquals(IllegalArgumentException::class.java, failure?.javaClass)
  }

  @Test
  fun `a preview naming a pixel device keeps its pixels`() {
    assertEquals(1080, resolve(AndroidPreviewInfo(device = "id:pixel_4a")).surfaceWidth)
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
    // so the unset axis keeps the device's size.
    val spec = resolve(AndroidPreviewInfo(heightDp = 500))

    assertEquals(1080, spec.surfaceWidth)
    assertEquals(1375, spec.surfaceHeight)
  }

  @Test
  fun `a preview larger than the device enlarges the surface`() {
    val spec = resolve(AndroidPreviewInfo(widthDp = 1000, heightDp = 1000))

    assertEquals(2750, spec.surfaceWidth)
    assertEquals(2750, spec.surfaceHeight)
  }

  @Test
  fun `a fractional widthDp floors the way Android's window does`() {
    // Measured: the Robolectric runtime renders @Preview(widthDp = 201) on the Pixel 4a as 552px
    // wide, not the 553 that rounding 201 x 2.75 = 552.75 would give.
    assertEquals(552, DesktopPreviewRenderSpec.flooredPx(201, 2.75f))
    assertEquals(0, DesktopPreviewRenderSpec.flooredPx(-1, 2.75f))
  }

  @Test
  fun `density is the product Android computes, not the quotient`() {
    // 213 / 160f and 213 * (1f / 160f) differ by one ULP, and Android multiplies. This pins the
    // exact float so a later refactor cannot quietly switch back to dividing.
    val spec = resolve(
      AndroidPreviewInfo(device = "spec:width=1080px,height=1920px,dpi=213"),
    )

    assertEquals(213 * (1f / 160f), spec.density, 0f)
  }

  @Test
  fun `a device named the long way resolves the same as its id`() {
    val byName = resolve(AndroidPreviewInfo(device = "name:Pixel 4a"))
    val byId = resolve(AndroidPreviewInfo(device = "id:pixel_4a"))

    assertEquals(byId, byName)
  }

  @Test
  fun `a device declared width-first in landscape stays landscape`() {
    // The spec's own dimension order contradicts the keyword here, and the keyword has to win:
    // measured, both runtimes render this 1920 wide.
    val keywordWins = resolve(
      AndroidPreviewInfo(
        device = "spec:width=800dp,height=1280dp,dpi=240,orientation=landscape",
      ),
    )
    val orderAlone = resolve(
      AndroidPreviewInfo(device = "spec:width=1280dp,height=800dp,dpi=240"),
    )

    assertEquals(1920, keywordWins.surfaceWidth)
    assertEquals(1200, keywordWins.surfaceHeight)
    assertEquals(keywordWins, orderAlone)
  }

  @Test
  fun `fontScale does not move the surface`() {
    val plain = resolve(AndroidPreviewInfo())
    val scaled = resolve(AndroidPreviewInfo(fontScale = 2f))

    assertEquals(plain, scaled)
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
