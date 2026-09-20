package com.github.takahirom.roborazzi

import kotlin.math.floor
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.android.device.DevicePreviewInfoParser
import sergio.sastre.composable.preview.scanner.android.device.domain.Device
import sergio.sastre.composable.preview.scanner.android.device.domain.Orientation

/**
 * The raster surface and density a single preview is rendered at on the Compose Desktop runtime.
 *
 * Resolved by [DesktopPreviewRenderSpec.resolve] from the preview's own `@Preview` options and the
 * [DesktopPreviewRenderProfile] configured for the test run. It is a plain value so the sizing
 * rules can be tested without starting a Compose surface.
 */
@ExperimentalRoborazziApi
data class DesktopPreviewRenderSpec(
  val surfaceWidth: Int,
  val surfaceHeight: Int,
  val density: Float,
) {
  companion object {
    /**
     * The surface the desktop runtime has always used when it has no device to size itself from.
     * It is only a floor: `widthDp`/`heightDp` enlarge it so `captureToImage()` can crop the full
     * `requiredSize` root bounds.
     */
    internal const val DEFAULT_SURFACE_WIDTH = 1024
    internal const val DEFAULT_SURFACE_HEIGHT = 768

    @OptIn(ExperimentalRoborazziApi::class)
    fun resolve(
      previewInfo: AndroidPreviewInfo,
      profile: DesktopPreviewRenderProfile,
    ): DesktopPreviewRenderSpec {
      val defaultDevice = profile.defaultDevice
        // No default device means the historical behaviour: density is pinned at 1 so 1dp == 1px,
        // and `device` is ignored entirely - including when the preview declares one, so that a
        // profile-less build renders exactly what it rendered before render profiles existed.
        ?: return DesktopPreviewRenderSpec(
          surfaceWidth = enlarge(DEFAULT_SURFACE_WIDTH, previewInfo.widthDp),
          surfaceHeight = enlarge(DEFAULT_SURFACE_HEIGHT, previewInfo.heightDp),
          density = 1f,
        )

      val deviceSpec = previewInfo.device.ifBlank { defaultDevice }
      val device = requireNotNull(DevicePreviewInfoParser.parse(deviceSpec)) {
        "Roborazzi: could not parse the preview device \"$deviceSpec\". It has to be written in " +
          "the same grammar as @Preview(device = ...): \"id:...\", \"name:...\" or \"spec:...\"."
      }
      val density = device.densityDpi / DENSITY_DPI_PER_DENSITY
      val (deviceWidthDp, deviceHeightDp) = device.screenSizeDp()

      return DesktopPreviewRenderSpec(
        surfaceWidth = override(toPx(deviceWidthDp, density), toPx(previewInfo.widthDp, density)),
        surfaceHeight = override(toPx(deviceHeightDp, density), toPx(previewInfo.heightDp, density)),
        density = density,
      )
    }

    /**
     * The device's screen in dp, the way the Robolectric runtime sees it.
     *
     * This deliberately goes through [Device.inDp] and truncates, because that is what
     * `RobolectricDeviceQualifierBuilder` does before handing the size to Robolectric as a
     * `w<n>dp-h<n>dp` qualifier. For a device declared in pixels the round trip loses a fraction,
     * and reproducing that loss is what makes the two runtimes agree: `id:pixel_4a` is 1080x2340 px
     * at 440dpi, which is 392dp wide, which is 1078px - not the 1080px it started from.
     */
    private fun Device.screenSizeDp(): Pair<Int, Int> {
      val inDp = inDp().dimensions
      val width = inDp.width.toInt()
      val height = inDp.height.toInt()
      // A landscape device is described by its natural portrait dimensions, and the `land`
      // qualifier is what turns it around, so mirror that here rather than trusting the order.
      return if (orientation == Orientation.LANDSCAPE) {
        maxOf(width, height) to minOf(width, height)
      } else {
        minOf(width, height) to maxOf(width, height)
      }
    }

    /**
     * Converts dp to px by flooring.
     *
     * `Dimensions.inPx(dpi)` from ComposablePreviewScanner is not used on purpose: it rounds with
     * `ceil`, which would make every capture a pixel wider and taller than the Robolectric one.
     */
    private fun toPx(dp: Int, density: Float): Int =
      if (dp > 0) floor(dp * density).toInt() else 0

    // A dp is 1px at 160dpi, by definition.
    private const val DENSITY_DPI_PER_DENSITY = 160f

    /** `widthDp`/`heightDp` of -1 (unset) leave the surface alone. */
    private fun enlarge(surface: Int, requested: Int): Int =
      if (requested > 0) maxOf(surface, requested) else surface

    /**
     * Replaces one axis of the device's screen with the size the preview asked for.
     *
     * The Robolectric runtime turns `widthDp`/`heightDp` into additive `w<n>dp` / `h<n>dp`
     * qualifiers, which resize the display rather than only growing it: measured on the
     * cross-runtime sample, `@Preview(widthDp = 200, heightDp = 120)` renders in a 550x330 window,
     * and `@Preview(heightDp = 500)` in a 1080x1375 one - the unset axis keeps the device's size.
     * Taking the larger of the two instead would leave the window at the full device size, so a
     * layout that reads `LocalWindowInfo.containerSize` or fills its parent would lay itself out
     * for a different window than it gets on Android, even where the cropped image happens to
     * match.
     */
    private fun override(deviceAxis: Int, requested: Int): Int =
      if (requested > 0) requested else deviceAxis
  }
}
