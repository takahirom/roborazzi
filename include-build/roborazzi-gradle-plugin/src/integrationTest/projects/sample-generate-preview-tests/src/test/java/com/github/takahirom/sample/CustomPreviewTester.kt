package com.github.takahirom.sample

import org.junit.Assert.*
import com.github.takahirom.roborazzi.*
import org.junit.rules.TestWatcher
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo

class CustomPreviewTester : ComposePreviewTester<AndroidPreviewInfo> by AndroidComposePreviewTester() {
  override fun options(): ComposePreviewTester.Options = super.options().copy(
    testLifecycleOptions = ComposePreviewTester.Options.JUnit4TestLifecycleOptions(
      testRuleFactory = {
        object : TestWatcher() {
          override fun starting(description: org.junit.runner.Description?) {
            println("JUnit4TestLifecycleOptions starting")
            super.starting(description)
          }

          override fun finished(description: org.junit.runner.Description?) {
            println("JUnit4TestLifecycleOptions finished")
            super.finished(description)
          }
        }
      }
    )
  )
}