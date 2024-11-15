package com.github.takahirom.sample

import org.junit.Assert.*
import com.github.takahirom.roborazzi.*
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot

class CustomPreviewTester : ComposePreviewTester<AndroidPreviewInfo> by AndroidComposePreviewTester() {
  val composeTestRule = createComposeRule()
  override fun options(): ComposePreviewTester.Options = super.options().copy(
    testLifecycleOptions = ComposePreviewTester.Options.JUnit4TestLifecycleOptions(
      testRuleFactory = {
        RuleChain.outerRule(
          object : TestWatcher() {
            override fun starting(description: org.junit.runner.Description?) {
              println("JUnit4TestLifecycleOptions starting")
            }

            override fun finished(description: org.junit.runner.Description?) {
              println("JUnit4TestLifecycleOptions finished")
            }
          }
        )
          .around(composeTestRule)
      }
    )
  )

  override fun test(preview: ComposablePreview<AndroidPreviewInfo>) {
    composeTestRule.setContent {
      preview()
    }
    composeTestRule.onRoot().captureRoboImage("${roborazziSystemPropertyOutputDirectory()}/${preview.methodName}.${roborazziContext().options.imageExtension}")
  }
}