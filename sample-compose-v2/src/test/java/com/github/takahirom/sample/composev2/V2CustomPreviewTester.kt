package com.github.takahirom.sample.composev2

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.github.takahirom.roborazzi.AndroidComposePreviewTester
import com.github.takahirom.roborazzi.ComposePreviewTester
import com.github.takahirom.roborazzi.ComposePreviewTester.TestParameter.JUnit4TestParameter.AndroidPreviewJUnit4TestParameter
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziActivity
import com.github.takahirom.roborazzi.registerRoborazziActivityToRobolectricIfNeeded
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher

@OptIn(ExperimentalRoborazziApi::class)
class V2CustomPreviewTester :
  ComposePreviewTester<AndroidPreviewJUnit4TestParameter> by AndroidComposePreviewTester() {
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
      },
      // Not required for the rules created above (reflection finds the scenario),
      // but demonstrates the explicit escape hatch for custom rules whose scenario
      // cannot be discovered via reflection.
      activityScenarioProvider = { composeTestRule ->
        @Suppress("UNCHECKED_CAST")
        (composeTestRule as AndroidComposeTestRule<ActivityScenarioRule<RoborazziActivity>, RoborazziActivity>)
          .activityRule.scenario
      }
    )
  )
}
