package com.github.takahirom.roborazzi

/**
 * Captures the screenshot next to a legend of the elements a screen reader focuses, in its
 * traversal order: label, role, state, value and text field properties of each element.
 *
 * The legend is rendered into the image, so it is recorded and verified like any other
 * screenshot, and a changed label, role, state or order shows up as a golden diff.
 *
 * Android only: it reads [android.view.accessibility.AccessibilityNodeInfo] and the Compose
 * semantics tree under Robolectric.
 */
@ExperimentalRoborazziApi
fun RoborazziOptions.CaptureType.Companion.AccessibilityLegend(): RoborazziOptions.CaptureType =
  AccessibilityLegendCaptureType

internal object AccessibilityLegendCaptureType : RoborazziOptions.CaptureType {
  override fun shouldTakeScreenshot(): Boolean = true
}
