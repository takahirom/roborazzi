package com.github.takahirom.sample

import com.github.takahirom.roborazzi.DefaultDesktopComposePreviewTester
import com.github.takahirom.roborazzi.DesktopComposePreviewTester
import com.github.takahirom.roborazzi.DesktopPreviewTestParameter
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalRoborazziApi::class, InternalRoborazziApi::class)
class CustomDesktopPreviewTester : DesktopComposePreviewTester by DefaultDesktopComposePreviewTester() {
  override fun options(): DesktopComposePreviewTester.Options =
    DesktopComposePreviewTester.defaultOptionsFromPlugin.copy(
      testLifecycleOptions = DesktopComposePreviewTester.Options.JUnit4TestLifecycleOptions(
        testRuleFactory = {
          object : TestWatcher() {
            override fun starting(description: Description) {
              println("CustomDesktopPreviewTester JUnit4TestLifecycleOptions starting")
            }
          }
        }
      )
    )

  override fun testParameters(): List<DesktopPreviewTestParameter> {
    println("CustomDesktopPreviewTester testParameters() is called")
    return DefaultDesktopComposePreviewTester().testParameters()
  }
}
