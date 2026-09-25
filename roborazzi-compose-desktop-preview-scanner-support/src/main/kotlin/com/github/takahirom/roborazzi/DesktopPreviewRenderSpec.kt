package com.github.takahirom.roborazzi

import kotlin.math.floor
import kotlin.math.roundToInt
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.android.device.DevicePreviewInfoParser
import sergio.sastre.composable.preview.scanner.android.device.domain.Device
import sergio.sastre.composable.preview.scanner.android.device.domain.Orientation
import sergio.sastre.composable.preview.scanner.android.device.domain.Unit as DeviceUnit

/**
 * The raster surface and density a single preview is rendered at on the Compose Desktop runtime.
 *
 * Resolved by [DesktopPreviewRenderSpec.resolve] from the preview's own `@Preview` options and the
 * [DesktopPreviewDeviceProfile] configured for the test run. It is a plain value so the sizing
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

    /**
     * The spec a capture should use, recorded as the scale that reached it.
     *
     * The recording is what [DesktopRenderScaleVerification] reads, so a custom tester that sizes
     * its own surface through this function passes the check without knowing it exists - which is
     * the path the failure message tells it to take.
     */
    @OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
    fun resolve(
      previewInfo: AndroidPreviewInfo,
      profile: DesktopPreviewDeviceProfile,
      renderScale: Double = 1.0,
    ): DesktopPreviewRenderSpec {
      DesktopRenderScaleVerification.markApplied(renderScale)
      return resolveWithoutRecording(previewInfo, profile, renderScale)
    }

    /**
     * The same sizing, for the callers that are not about to capture anything.
     *
     * Scene grouping resolves every preview up front to decide which ones share a surface. Letting
     * that count as "the scale reached the capture" would leave the check passing for a tester that
     * then captured at a different density, which is the one thing it is there to catch.
     */
    @OptIn(ExperimentalRoborazziApi::class)
    internal fun resolveWithoutRecording(
      previewInfo: AndroidPreviewInfo,
      profile: DesktopPreviewDeviceProfile,
      renderScale: Double = 1.0,
    ): DesktopPreviewRenderSpec {
      require(renderScale.isFinite() && renderScale > 0.0) {
        "renderScale must be finite and greater than 0, but was $renderScale"
      }
      // A device the preview declares always wins; the profile only supplies one for previews that
      // declare none. With no device from either, the historical behaviour applies: density is
      // pinned at 1 so 1dp == 1px, on a surface of at least 1024x768.
      //
      // There is no Robolectric counterpart to copy the arithmetic from for that case, so the scale
      // goes through the same integer dpi a device uses, reading the pinned density as 160dpi. At
      // scale 1 that is 1f again, so a device-less preview's output is unchanged.
      val deviceSpec = previewInfo.device.ifBlank { profile.defaultDevice }
        ?: run {
          val density = scaledDensity(DENSITY_DEFAULT, renderScale)
          return DesktopPreviewRenderSpec(
            surfaceWidth = enlarge(
              flooredPx(DEFAULT_SURFACE_WIDTH, density),
              flooredPx(previewInfo.widthDp, density),
            ),
            surfaceHeight = enlarge(
              flooredPx(DEFAULT_SURFACE_HEIGHT, density),
              flooredPx(previewInfo.heightDp, density),
            ),
            density = density,
          )
        }

      val device = requireNotNull(DevicePreviewInfoParser.parse(deviceSpec)) {
        "Roborazzi: could not parse the preview device \"$deviceSpec\". It has to be written in " +
          "the same grammar as @Preview(device = ...): \"id:...\", \"name:...\" or \"spec:...\"."
      }
      val density = scaledDensity(device.densityDpi, renderScale)
      val (deviceWidthPx, deviceHeightPx) = device.screenSizePx(renderScale)

      return DesktopPreviewRenderSpec(
        surfaceWidth = override(deviceWidthPx, toPx(previewInfo.widthDp, density)),
        surfaceHeight = override(deviceHeightPx, toPx(previewInfo.heightDp, density)),
        density = density,
      )
    }

    /**
     * Scales a device's dpi the way `PreviewRenderScaleOption` does on the Robolectric runtime.
     *
     * The scale is applied to the integer dpi and rounded there, not to the density, so that both
     * runtimes render at a dpi Android could actually report. At scale 1 this returns the device's
     * own density unchanged, down to the bit, because the multiplication is skipped.
     */
    private fun scaledDensity(densityDpi: Int, renderScale: Double): Float =
      scaledDpi(densityDpi, renderScale) * DENSITY_DEFAULT_SCALE

    /** The device's dpi after [renderScale], as an integer dpi Android could report. */
    private fun scaledDpi(densityDpi: Int, renderScale: Double): Int =
      if (renderScale == 1.0) densityDpi
      else (densityDpi * renderScale).roundToInt().coerceAtLeast(1)

    /**
     * Scales a device's own pixels by the same ratio [scaledDpi] moved its dpi.
     *
     * A pixel device has no dp to scale, so the pixels are scaled directly. At scale 1 the ratio
     * is 1 and the device's pixels survive exactly, which is the point of the pixel path; at 0.5 a
     * 1080x2400 device is 540x1200, where going through its 411x914 dp would give 539x1199.
     */
    private fun scalePx(px: Int, densityDpi: Int, renderScale: Double): Int {
      if (renderScale == 1.0) return px
      return floor(px.toDouble() * scaledDpi(densityDpi, renderScale) / densityDpi)
        .toInt()
        .coerceAtLeast(1)
    }

    /**
     * The device's screen in the pixels it is rendered at, after [renderScale].
     *
     * The Robolectric runtime reaches the same number with a single `floor(dp * density)` - it
     * keeps `Configuration.screenWidthDp` exactly as the qualifier gave it and derives the pixels
     * from that, measured: `w393dp` at 440dpi is a 1080px window, `w411dp` at 420dpi a 1078px one.
     * So a device written in dp is floored once here too, and a device written in pixels is
     * rendered at exactly those pixels rather than at the dp they happen to round to.
     *
     * The two spellings are not interchangeable at the edges. `id:pixel_4a` is 1080x2340px at
     * 440dpi and comes out 1080x2340; the 392dp that `1080 / 2.75` truncates to would be 1078.
     * ComposablePreviewScanner builds the Robolectric qualifier from that truncated dp, so a
     * preview that names `id:pixel_4a` is 1078x2337 on the Robolectric runtime and 1080x2340
     * here. The difference is at most a pixel or two and belongs to the qualifier builder, not to
     * either runtime's arithmetic.
     */
    private fun Device.screenSizePx(renderScale: Double): Pair<Int, Int> {
      val (width, height) = when (dimensions.unit) {
        DeviceUnit.PX -> scalePx(dimensions.width.toInt(), densityDpi, renderScale) to
          scalePx(dimensions.height.toInt(), densityDpi, renderScale)

        DeviceUnit.DP -> {
          val density = scaledDensity(densityDpi, renderScale)
          flooredPx(dimensions.width.toInt(), density) to
            flooredPx(dimensions.height.toInt(), density)
        }
      }
      // A landscape device is described by its natural portrait dimensions, and the `land`
      // qualifier is what turns it around, so mirror that here rather than trusting the order.
      return if (orientation == Orientation.LANDSCAPE) {
        maxOf(width, height) to minOf(width, height)
      } else {
        minOf(width, height) to maxOf(width, height)
      }
    }

    /**
     * Converts dp to px the way Android's window sizing does, by flooring.
     *
     * `Dimensions.inPx(dpi)` from ComposablePreviewScanner is not used on purpose: it rounds with
     * `ceil`, which would make every capture a pixel wider and taller than the Robolectric one.
     *
     * An unset dp (-1) converts to 0, which every caller reads as "not specified".
     */
    @ExperimentalRoborazziApi
    fun flooredPx(dp: Int, density: Float): Int =
      if (dp > 0) floor(dp * density).toInt() else 0

    private fun toPx(dp: Int, density: Float): Int = flooredPx(dp, density)

    /**
     * The same constant Android's `DisplayMetrics.DENSITY_DEFAULT_SCALE` holds: `1f / 160f`.
     *
     * Android multiplies by it rather than dividing by 160, and the two disagree by one ULP at
     * 213dpi and 411dpi, so multiplying is what keeps the density bit-identical to the one the
     * Robolectric runtime hands Compose.
     */
    private const val DENSITY_DEFAULT_SCALE = 1f / 160f

    /** `DisplayMetrics.DENSITY_DEFAULT`: the dpi at which 1dp is 1px. */
    private const val DENSITY_DEFAULT = 160

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
