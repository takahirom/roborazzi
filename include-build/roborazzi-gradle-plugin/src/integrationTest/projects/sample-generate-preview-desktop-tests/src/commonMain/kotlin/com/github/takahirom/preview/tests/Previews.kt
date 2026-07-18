package com.github.takahirom.preview.tests

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
fun PreviewNormal() {
  MaterialTheme {
    Text(
      modifier = Modifier.padding(8.dp),
      text = "Hello, Desktop Preview!"
    )
  }
}

@Preview
@Composable
fun PreviewHeadline() {
  MaterialTheme {
    Text(
      style = MaterialTheme.typography.headlineMedium,
      text = "Desktop Preview Headline"
    )
  }
}

@Preview
@Composable
private fun PreviewWithPrivate() {
  MaterialTheme {
    Text(text = "Private Desktop Preview")
  }
}
