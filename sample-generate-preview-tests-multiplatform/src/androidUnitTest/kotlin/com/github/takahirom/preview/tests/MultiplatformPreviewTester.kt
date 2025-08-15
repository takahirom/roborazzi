package com.github.takahirom.preview.tests

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.github.takahirom.roborazzi.*
import com.github.takahirom.roborazzi.ComposePreviewTester.TestParameter.JUnit4TestParameter
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import sergio.sastre.composable.preview.scanner.common.CommonComposablePreviewScanner
import sergio.sastre.composable.preview.scanner.common.CommonPreviewInfo

@OptIn(ExperimentalRoborazziApi::class)
class MultiplatformPreviewTester : ComposePreviewTester<JUnit4TestParameter<CommonPreviewInfo>> {
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

  override fun testParameters(): List<JUnit4TestParameter<CommonPreviewInfo>> {
    val options = options()
    return CommonComposablePreviewScanner()
      .scanPackageTrees(*options.scanOptions.packages.toTypedArray())
      .getPreviews()
      .map {
        JUnit4TestParameter(
          (options.testLifecycleOptions as ComposePreviewTester.Options.JUnit4TestLifecycleOptions).composeRuleFactory,
          it
        )
      }
  }

  override fun test(testParameter: JUnit4TestParameter<CommonPreviewInfo>) {
    val preview = testParameter.preview
    val screenshotNameSuffix = preview.previewIndex?.let { "_" + preview.previewIndex }.orEmpty()
    
    testParameter.composeTestRule.setContent {
      preview()
    }
    testParameter.composeTestRule.onRoot()
      .captureRoboImage(DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH + "/" + preview.methodName + screenshotNameSuffix + ".png")
  }
}