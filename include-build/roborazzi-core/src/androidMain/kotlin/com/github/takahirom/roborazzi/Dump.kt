package com.github.takahirom.roborazzi

data class Dump(
  val takeScreenShot: Boolean = canScreenshot(),
  val basicSize: Int = 600,
  val depthSlideSize: Int = 30,
  val query: ((RoboComponent) -> Boolean)? = null,
  val explanation: ((RoboComponent) -> String?) = DefaultExplanation,
) : RoborazziOptions.CaptureType {
  override fun shouldTakeScreenshot(): Boolean = takeScreenShot
  companion object {
    val DefaultExplanation: ((RoboComponent) -> String) = {
      it.text
    }
    val AccessibilityExplanation: ((RoboComponent) -> String) = {
      it.accessibilityText
    }

  }
}