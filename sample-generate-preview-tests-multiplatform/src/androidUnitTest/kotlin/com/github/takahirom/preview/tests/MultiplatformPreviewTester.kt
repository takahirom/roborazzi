package com.github.takahirom.preview.tests

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.github.takahirom.roborazzi.*
import com.github.takahirom.roborazzi.ComposePreviewTester.TestParameter.JUnit4TestParameter
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import sergio.sastre.composable.preview.scanner.jvm.JvmAnnotationInfo
import sergio.sastre.composable.preview.scanner.jvm.JvmAnnotationScanner

@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
class MultiplatformPreviewTester : ComposePreviewTester<JUnit4TestParameter<JvmAnnotationInfo>> {
  override fun options(): ComposePreviewTester.Options = super.options().copy(
    testLifecycleOptions = ComposePreviewTester.Options.JUnit4TestLifecycleOptions(
      composeRuleFactory = {
        @Suppress("UNCHECKED_CAST")
        createAndroidComposeRule<RoborazziActivity>() as AndroidComposeTestRule<ActivityScenarioRule<out androidx.activity.ComponentActivity>, *>
      },
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
    )
  )

  override fun testParameters(): List<JUnit4TestParameter<JvmAnnotationInfo>> {
    val options = options()
    return JvmAnnotationScanner("org.jetbrains.compose.ui.tooling.preview.Preview")
      .scanPackageTrees(*options.scanOptions.packages.toTypedArray())
      .getPreviews()
      .map {
        JUnit4TestParameter(
          (options.testLifecycleOptions as ComposePreviewTester.Options.JUnit4TestLifecycleOptions).composeRuleFactory(),
          it
        )
      }
  }

  override fun test(testParameter: JUnit4TestParameter<JvmAnnotationInfo>) {
    val preview = testParameter.preview
    testParameter.composeTestRule.setContent {
      preview()
    }
    testParameter.composeTestRule.onRoot()
      .captureRoboImage(DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH + "/" + preview.methodName + ".png")
  }
}