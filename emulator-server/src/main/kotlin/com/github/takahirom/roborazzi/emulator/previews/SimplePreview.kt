package com.github.takahirom.roborazzi.emulator.previews;

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun Simple() {
  println("Simple")
  Box(Modifier.fillMaxSize()) {
    Text("Simple")

  }

  LaunchedEffect(Unit) {
    println("Launched Effect")
  }
}

@Composable
@Preview
fun SimplePreview() {
  println("SimplePreview")
  Simple()
}
