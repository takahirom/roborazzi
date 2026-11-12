package com.github.takahirom.roborazzi

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalRoborazziApi::class)
class PreviewClassInitializationTest {
  @After fun resetPluginOptions() {
    ComposePreviewTester.defaultOptionsFromPlugin = ComposePreviewTester.Options()
  }

  @Test fun testParametersDoNotInitializeTheClassDeclaringThePreview() {
    ComposePreviewTester.defaultOptionsFromPlugin = ComposePreviewTester.Options(
      scanOptions = ComposePreviewTester.Options.ScanOptions(
        packages = listOf("com.github.takahirom.roborazzi.previewclassinit"),
      ),
    )

    val parameters = AndroidComposePreviewTester().testParameters()

    assertEquals(listOf("PreviewInFileWithFailingInit"), parameters.map { it.preview.methodName })
  }
}
