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
    renderScale: Double = 1.0,
  ) = DesktopPreviewRenderSpec.resolve(previewInfo, profile, renderScale)

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
    // Measured: the Robolectric runtime renders a device-less preview at 1078x2337 under its
    // default Pixel 4a qualifiers.
    val spec = resolve(AndroidPreviewInfo())

    assertEquals(1078, spec.surfaceWidth)
    assertEquals(2337, spec.surfaceHeight)
    assertEquals(2.75f, spec.density)
  }

  @Test
  fun `the profile default goes through the qualifier round trip and a preview device does not`() {
    // goals.md measured Robolectric at 1076 for the Pixel 6 qualifiers (w411dp at 420dpi): the
    // base configuration's dp becomes pixels and is read back as dp before becoming the size,
    // 411 -> 1078px -> 410dp -> 1076px. A preview that names the same device instead gets an
    // additive qualifier built from the parsed device, which is a plain floor: 411 -> 1078.
    val asProfileDefault = DesktopPreviewRenderSpec.resolve(
      AndroidPreviewInfo(),
      DesktopPreviewRenderProfile.AndroidCompatible
        .copy(defaultDevice = "spec:width=411dp,height=891dp,dpi=420"),
    )
    val asPreviewDevice = resolve(
      AndroidPreviewInfo(device = "spec:width=411dp,height=891dp,dpi=420"),
    )

    assertEquals(1076, asProfileDefault.surfaceWidth)
    assertEquals(1078, asPreviewDevice.surfaceWidth)
  }

  @Test
  fun `a default device declared in pixels skips the round trip it has already made`() {
    // The scanner's Pixel 4a is a pixel-table entry, so `inDp()` has already taken 2340px down to
    // 850dp. Rounding a second time would reach 849dp and a 2334px surface, three pixels short of
    // what the Robolectric runtime was measured to produce.
    val viaPixelTable = DesktopPreviewRenderSpec.resolve(
      AndroidPreviewInfo(),
      DesktopPreviewRenderProfile.AndroidCompatible.copy(defaultDevice = "id:pixel_4a"),
    )

    assertEquals(DesktopPreviewRenderSpec(1078, 2337, 2.75f), viaPixelTable)
  }

  @Test
  fun `a blank default device is rejected because the parser would take it as a device`() {
    val failure = runCatching {
      DesktopPreviewRenderProfile.AndroidCompatible.copy(defaultDevice = "")
    }.exceptionOrNull()

    assertEquals(IllegalArgumentException::class.java, failure?.javaClass)
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

  // --- renderScale ------------------------------------------------------------------------------
  //
  // Measured on the cross-runtime sample by recording `recordRoborazziDebug` with
  // `-Proborazzi.renderScale=0.5`, with the Robolectric side on `RobolectricDeviceQualifiers.Pixel4a`
  // and sdk 35. The widths below are the widths of the PNGs that run produced.

  @Test
  fun `a preview that names a device scales its density and floors the pixels`() {
    // Measured: PhoneSpecText is 1078px wide at scale 1 and 539px at 0.5. 420dpi halves to 210,
    // so 411dp * 1.3125 = 539.4 -> 539.
    val phone = AndroidPreviewInfo(device = "spec:width=411dp,height=891dp,dpi=420")

    assertEquals(1078, resolve(phone).surfaceWidth)
    assertEquals(539, resolve(phone, renderScale = 0.5).surfaceWidth)
    assertEquals(210 / 160f, resolve(phone, renderScale = 0.5).density)
  }

  @Test
  fun `a scaled device-less preview uses the base dp, not the dp the round trip lowers`() {
    // Measured: DefaultText is 1078px wide at scale 1 and 540px at 0.5 - not 539.
    //
    // At scale 1 the Robolectric side applies the base configuration as the @Config device and
    // reads the dp back from its truncated pixels (393 -> 1080 -> 392 -> 1078). A scaled run
    // instead builds an additive `w393dp-h851dp-220dpi` qualifier from the dp the configuration
    // carries, and 393 * 1.375 = 540.375 -> 540. Dropping the round trip with the scale is what
    // reproduces that.
    assertEquals(1078, resolve(AndroidPreviewInfo()).surfaceWidth)
    assertEquals(540, resolve(AndroidPreviewInfo(), renderScale = 0.5).surfaceWidth)
  }

  @Test
  fun `widthDp and heightDp follow the scaled density too`() {
    val wide = AndroidPreviewInfo(widthDp = 2000, heightDp = 120)

    assertEquals(5500, resolve(wide).surfaceWidth)
    assertEquals(2750, resolve(wide, renderScale = 0.5).surfaceWidth)
    // Both axes are the preview's own, so the height follows the scaled density too.
    assertEquals(165, resolve(wide, renderScale = 0.5).surfaceHeight)
  }

  @Test
  fun `a third of the density rounds the dpi rather than the pixels`() {
    // Measured at -Proborazzi.renderScale=0.3333333333333333: 420dpi * 1/3 is 140dpi exactly, so
    // 411dp * 0.875 = 359.6 -> 359, while scaling the pixel count would give 359 from 1078 / 3 by
    // a different route and 358 from the dp being re-derived. Pinning the dpi is what keeps the
    // two runtimes together.
    val phone = AndroidPreviewInfo(device = "spec:width=411dp,height=891dp,dpi=420")
    val third = 1.0 / 3.0

    assertEquals(140 / 160f, resolve(phone, renderScale = third).density)
    assertEquals(359, resolve(phone, renderScale = third).surfaceWidth)
  }

  @Test
  fun `a tiny scale still leaves a renderable dpi`() {
    // (440 * 0.001).roundToInt() is 0, and a density of 0 makes every dimension 0 pixels wide.
    val spec = resolve(AndroidPreviewInfo(), renderScale = 0.001)

    assertEquals(1 / 160f, spec.density)
    assertEquals(2, spec.surfaceWidth)
  }

  @Test
  fun `the profile without a default device scales its pinned density as 160dpi`() {
    val spec = resolve(
      AndroidPreviewInfo(widthDp = 2000, heightDp = 120),
      DesktopPreviewRenderProfile.Desktop,
      renderScale = 0.5,
    )

    // 1dp is half a pixel now, so the historical 1024x768 surface halves with it.
    assertEquals(DesktopPreviewRenderSpec(1000, 384, 0.5f), spec)
  }

  @Test
  fun `a scale of one changes nothing on the profile without a default device`() {
    assertEquals(
      resolve(AndroidPreviewInfo(), DesktopPreviewRenderProfile.Desktop),
      resolve(AndroidPreviewInfo(), DesktopPreviewRenderProfile.Desktop, renderScale = 1.0),
    )
  }

  @Test
  fun `a scale that is not a number is rejected where it is configured`() {
    listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { scale ->
      val failure = runCatching { resolve(AndroidPreviewInfo(), renderScale = scale) }.exceptionOrNull()
      assertEquals(
        "renderScale must be finite and greater than 0, but was $scale",
        failure?.message,
      )
    }
  }
}
