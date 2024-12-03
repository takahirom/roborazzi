package com.github.takahirom.preview.tests

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Wallpapers
import androidx.compose.ui.unit.dp

@Preview
@Composable
fun PreviewNormal() {
  MaterialTheme {
    Card(
      Modifier
        .width(180.dp)
    ) {
      Text(
        modifier = Modifier.padding(8.dp),
        text = "Generate Preview Test Sample"
      )
    }
  }
}

@Preview(
  uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun PreviewDarkMode() {
  val isSystemInDarkTheme = isSystemInDarkTheme()
  MaterialTheme(
    colorScheme = if (isSystemInDarkTheme) {
      darkColorScheme()
    } else {
      lightColorScheme()
    }
  ) {
    Card(
      Modifier
        .width(180.dp)
    ) {
      Text(
        modifier = Modifier.padding(8.dp),
        text = "Generate Preview Test Sample"
      )
    }
  }
}

@Preview(
  name = "Preview Name",
  // These properties are not supported by Roborazzi yet.
  group = "Preview Group",
  apiLevel = 30,
  widthDp = 320,
  heightDp = 640,
  locale = "ja-rJP",
  fontScale = 1.5f,
)
@Composable
fun PreviewWithProperties1() {
  Card(
    Modifier
      .width(100.dp)
  ) {
    Text(
      modifier = Modifier.padding(8.dp),
      text = "Hello, World!"
    )
  }
}

@Preview(
  showSystemUi = true,
  showBackground = true,
  backgroundColor = 0xFF0000FF,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
  device = Devices.NEXUS_5,
  wallpaper = Wallpapers.GREEN_DOMINATED_EXAMPLE,
)
@Composable
fun PreviewWithProperties2() {
  Card(
    Modifier
      .width(100.dp)
  ) {
    Text(
      modifier = Modifier.padding(8.dp),
      text = "Hello, World!"
    )
  }
}

@Preview
@Composable
fun PreviewDialog() {
  MaterialTheme {
    AlertDialog(
      onDismissRequest = {},
      confirmButton = @Composable { Text("Confirm") },
      text = @Composable { Text("Generate Preview Test Sample!") }
    )
  }
}
