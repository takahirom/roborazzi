package com.github.takahirom.roborazzi.sample.android.multiplatform

import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun Greeting() {
  var text by remember { mutableStateOf("Hello, Android Multiplatform!") }

  MaterialTheme {
    Button(
      modifier = Modifier.testTag("greeting-button"),
      onClick = {
        text = "Clicked!"
      }) {
      Text(
        style = MaterialTheme.typography.h5,
        text = text
      )
    }
  }
}
