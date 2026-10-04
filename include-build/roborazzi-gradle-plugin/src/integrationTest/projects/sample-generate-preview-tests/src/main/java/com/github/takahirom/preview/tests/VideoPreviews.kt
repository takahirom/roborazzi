package com.github.takahirom.preview.tests

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.github.takahirom.roborazzi.annotations.PreviewVideoOptions
import com.github.takahirom.roborazzi.annotations.RoboComposePreviewOptions
import kotlinx.coroutines.delay

@RoboComposePreviewOptions(
  videoOptions = [PreviewVideoOptions(durationMillis = 1000L, fps = 10)]
)
@Preview
@Composable
fun PreviewWithVideo() {
  var visible by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    delay(300)
    visible = true
  }
  MaterialTheme {
    Text(text = if (visible) "Video content" else "Video waiting...")
  }
}
