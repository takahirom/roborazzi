package com.github.takahirom.roborazzi.previewclassinit

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

// Initializing this file's class throws, like a top-level value that needs the Android
// framework and is only usable inside the Robolectric sandbox.
private val valueThatFailsOnInit: String =
  error("The class declaring a preview must not be initialized while scanning previews")

@Preview
@Composable
fun PreviewInFileWithFailingInit() {
  BasicText(valueThatFailsOnInit)
}
