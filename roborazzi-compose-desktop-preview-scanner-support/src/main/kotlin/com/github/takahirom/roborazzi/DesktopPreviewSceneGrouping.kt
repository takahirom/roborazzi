package com.github.takahirom.roborazzi

import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

/**
 * What has to be equal for two previews to be captured in the same Compose scene.
 *
 * Most `@Preview` options do not appear here. [DefaultDesktopComposePreviewTester] hands density,
 * fontScale, the night bit, widthDp/heightDp and the background to the composition through
 * `CompositionLocalProvider` and modifiers, so they change what is composed, not the scene that
 * composes it. Only three things are properties of the scene itself:
 *
 *  - the raster surface, which `runDesktopComposeUiTest(width, height)` fixes for the whole scene,
 *  - the JVM default locale, which is process-global and is set around the scene, not inside it,
 *  - whether the clock is driven by hand, which is a mode the scene is opened in.
 *
 * @see reusable for why a hand-driven clock stops a scene from being shared at all.
 */
internal data class DesktopPreviewSceneKey(
  val surfaceWidth: Int,
  val surfaceHeight: Int,
  val locale: String,
  /**
   * False when the previews in this group each need their own scene.
   *
   * A preview annotated with `@RoboComposePreviewOptions(manualClockOptions = ...)` is captured
   * with `mainClock.autoAdvance = false` and the clock advanced by hand. An infinite animation
   * takes its phase from the scene's absolute clock rather than from its own composition, and the
   * test clock can only be advanced, never rewound - so the second preview in a shared scene would
   * start where the first one left off and render a different frame. Recomposing the subtree from
   * scratch does not help, because the phase was never held in the composition. Measured on the
   * cross-runtime sample: `EndlessAnimation` and `EndlessSpinner` differ from their per-scene
   * capture under every variant tried (plain swap, `key()`, a frame before the advance, and
   * waiting for idle before the advance).
   */
  val reusable: Boolean,
)

/**
 * The scene [parameter] needs.
 *
 * A preview whose device spec cannot be parsed gets a scene of its own instead of failing here.
 * Grouping runs before any capture does, so throwing would take down every preview in the shard
 * rather than the one that is broken; left on its own, it reaches [DesktopPreviewRenderSpec.resolve]
 * at capture time and fails there with the parser's message, exactly as it does without scene reuse.
 */
internal fun desktopPreviewSceneKey(
  parameter: DesktopPreviewTestParameter,
  profile: DesktopPreviewDeviceProfile,
  renderScale: Double = 1.0,
): DesktopPreviewSceneKey {
  val renderSpec = try {
    DesktopPreviewRenderSpec.resolveWithoutRecording(
      parameter.preview.previewInfo, profile, renderScale,
    )
  } catch (unparsableDevice: IllegalArgumentException) {
    return DesktopPreviewSceneKey(
      surfaceWidth = 0,
      surfaceHeight = 0,
      locale = parameter.preview.previewInfo.locale,
      reusable = false,
    )
  }
  return DesktopPreviewSceneKey(
    surfaceWidth = renderSpec.surfaceWidth,
    surfaceHeight = renderSpec.surfaceHeight,
    locale = parameter.preview.previewInfo.locale,
    reusable = parameter.manualClockOptions == null,
  )
}

/**
 * Splits [parameters] into the runs that can share one scene, keeping the given order.
 *
 * Order is preserved rather than sorted: the caller has already decided the order (the generated
 * test sorts by a stable identifier so that shards agree), and reordering here would change which
 * preview sees which JVM-global state, which is the one kind of drift scene reuse cannot fix.
 *
 * A group of one is returned like any other; the caller captures it per-scene either way, so there
 * is nothing to special-case.
 */
internal fun groupDesktopPreviewsByScene(
  parameters: List<DesktopPreviewTestParameter>,
  profile: DesktopPreviewDeviceProfile,
  renderScale: Double = 1.0,
): List<List<DesktopPreviewTestParameter>> {
  val groups = mutableListOf<MutableList<DesktopPreviewTestParameter>>()
  val indexByKey = mutableMapOf<DesktopPreviewSceneKey, Int>()
  parameters.forEach { parameter ->
    val key = desktopPreviewSceneKey(parameter, profile, renderScale)
    if (!key.reusable) {
      groups.add(mutableListOf(parameter))
      return@forEach
    }
    val index = indexByKey.getOrPut(key) {
      groups.add(mutableListOf())
      groups.size - 1
    }
    groups[index].add(parameter)
  }
  return groups
}

/** Reads the preview's declaring method, for log messages that have to name a preview. */
internal fun ComposablePreview<AndroidPreviewInfo>.describe(): String = "$declaringClass.$methodName"
