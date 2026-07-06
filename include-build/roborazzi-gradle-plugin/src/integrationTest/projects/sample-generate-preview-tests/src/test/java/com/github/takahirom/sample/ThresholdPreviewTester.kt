package com.github.takahirom.sample

import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.*
import com.github.takahirom.roborazzi.ComposePreviewTester.TestParameter.JUnit4TestParameter.AndroidPreviewJUnit4TestParameter

/**
 * Compile check for the "Advanced: Custom ComposePreviewTester Implementation"
 * example in docs/topics/preview_support.md. Keep this in sync with the docs.
 */
@OptIn(ExperimentalRoborazziApi::class)
class ThresholdPreviewTester :
  ComposePreviewTester<AndroidPreviewJUnit4TestParameter> by AndroidComposePreviewTester(
    capturer = { parameter ->
      val customOptions = parameter.roborazziOptions.copy(
        compareOptions = parameter.roborazziOptions.compareOptions.copy(
          // Set custom comparison threshold (0.0 = exact match, 1.0 = ignore differences)
          imageComparator = SimpleImageComparator(maxDistance = 0.01f)
        )
      )
      AndroidComposePreviewTester.DefaultCapturer().capture(
        parameter.copy(roborazziOptions = customOptions)
      )
    }
  )
