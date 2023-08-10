package com.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.Dump.Companion.DefaultExplanation

fun RoborazziOptions.CaptureType.Companion.Dump(
  takeScreenShot: Boolean = canScreenshot(),
  basicSize: Int = 600,
  depthSlideSize: Int = 30,
  query: ((RoboComponent) -> Boolean)? = null,
  explanation: ((RoboComponent) -> String?) = DefaultExplanation,
): RoborazziOptions.CaptureType = com.github.takahirom.roborazzi.Dump(
  takeScreenShot = takeScreenShot,
  basicSize = basicSize,
  depthSlideSize = depthSlideSize,
  query = query,
  explanation = explanation,
)

data class Dump(
  val takeScreenShot: Boolean,
  val basicSize: Int,
  val depthSlideSize: Int,
  val query: ((RoboComponent) -> Boolean)?,
  val explanation: ((RoboComponent) -> String?),
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