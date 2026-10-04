package com.github.takahirom.sample.previews

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
fun GreetingPreview() {
  MaterialTheme {
    Text(text = "Hello from a common-style @Preview on Desktop!")
  }
}

@Preview
@Composable
fun HeadlinePreview() {
  MaterialTheme {
    Text(style = MaterialTheme.typography.h3, text = "Headline preview")
  }
}
