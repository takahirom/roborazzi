package com.github.takahirom.roborazzi

import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator

data class ATFAccessibilityChecker(
  val accessibilityValidator: AccessibilityValidator
) : RoborazziOptions.AccessibilityChecker

fun RoborazziOptions.AccessibilityChecker.Companion.atf(
  preset: AccessibilityCheckPreset? = AccessibilityCheckPreset.LATEST,
  config: AccessibilityValidator.() -> Unit
) =
  ATFAccessibilityChecker(accessibilityValidator = AccessibilityValidator().apply {
    setCheckPreset(preset)
  }.apply(config))