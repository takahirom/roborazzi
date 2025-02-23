package com.github.takahirom.preview.tests

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.github.takahirom.roborazzi.ComposePreviewTester
import com.github.takahirom.roborazzi.DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH
import com.github.takahirom.roborazzi.RoborazziActivity
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.registerRoborazziActivityToRobolectricIfNeeded
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import sergio.sastre.composable.preview.scanner.jvm.JvmAnnotationInfo
import sergio.sastre.composable.preview.scanner.jvm.JvmAnnotationScanner

@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
class MultiplatformPreviewTester : ComposePreviewTester<JvmAnnotationInfo> {
  @Suppress("UNCHECKED_CAST")
  val composeTestRule =
    createAndroidComposeRule<RoborazziActivity>() as AndroidComposeTestRule<ActivityScenarioRule<out androidx.activity.ComponentActivity>, *>

  override fun options(): ComposePreviewTester.Options = super.options().copy(
    testLifecycleOptions = ComposePreviewTester.Options.JUnit4TestLifecycleOptions(
      composeRuleFactory = { composeTestRule },
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

  override fun testParameters(): List<ComposePreviewTester.TestParameter<JvmAnnotationInfo>> {
    return JvmAnnotationScanner("org.jetbrains.compose.ui.tooling.preview.Preview")
      .scanPackageTrees(*options().scanOptions.packages.toTypedArray())
      .getPreviews()
      .map {
        ComposePreviewTester.TestParameter.JUnit4TestParameter(
          composeTestRule,
          it
        )
      }
  }

  override fun test(testParameter: ComposePreviewTester.TestParameter<JvmAnnotationInfo>) {
    if (testParameter !is ComposePreviewTester.TestParameter.JUnit4TestParameter<*>) {
      throw IllegalArgumentException()
    }
    val preview = testParameter.preview
    testParameter.composeTestRule.setContent {
      preview()
    }
    testParameter.composeTestRule.onRoot()
      .captureRoboImage(DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH + "/" + preview.methodName + ".png")
  }
}