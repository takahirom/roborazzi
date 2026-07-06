package com.github.takahirom.preview.tests

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.*
import com.github.takahirom.roborazzi.ComposePreviewTester.TestParameter.JUnit4TestParameter.AndroidPreviewJUnit4TestParameter
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher

@OptIn(ExperimentalRoborazziApi::class)
class V2CustomPreviewTester : ComposePreviewTester<AndroidPreviewJUnit4TestParameter> by AndroidComposePreviewTester() {
  override fun options(): ComposePreviewTester.Options = super.options().copy(
    testLifecycleOptions = ComposePreviewTester.Options.JUnit4TestLifecycleOptions(
      // Use v2's createAndroidComposeRule - returns ComposeContentTestRule
      composeRuleFactory = { createAndroidComposeRule<RoborazziActivity>() },
      testRuleFactory = { composeTestRule ->
        RuleChain.outerRule(
          object : TestWatcher() {
            override fun starting(description: org.junit.runner.Description?) {
              super.starting(description)
              registerRoborazziActivityToRobolectricIfNeeded()
            }
          })
          .around(composeTestRule)
      }
    ),
    // The plugin does not allow setting annotationFilter alongside a custom tester,
    // so apply the default RoboPreviewExclude filter here to match the module's behavior.
    scanOptions = super.options().scanOptions.copy(
      annotationFilter = AnnotationFilter.Filter.RoboPreviewExclude
    )
  )
}
