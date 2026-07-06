package com.github.takahirom.sample.composev2

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
fun PreviewSimpleText() {
  BasicText("Preview rendered with Compose Testing v2")
}

@Preview(widthDp = 320, heightDp = 100)
@Composable
fun PreviewPaddedText() {
  BasicText(
    text = "Padded preview with Compose Testing v2",
    modifier = Modifier.padding(16.dp)
  )
}
