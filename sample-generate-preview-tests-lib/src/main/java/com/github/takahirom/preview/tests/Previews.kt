package com.github.takahirom.preview.tests

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

/**
 * Annotation for previewing multiple themes.
 */
@Preview(
  name = "LightMode",
  group = "Theme",
  uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Preview(
  name = "DarkMode",
  group = "Theme",
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
annotation class MultiThemePreviews

@Preview
@Composable
fun LibraryComposable() {
  MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
  ) {
    Card(
      modifier = Modifier
          .padding(16.dp)
          .width(200.dp)
    ) {
      Text("Hello, World!")
    }
  }
}

@MultiThemePreviews
@Composable
fun LibraryComposableUsingMultiThemePreviews() {
  MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
  ) {
    Card(
      modifier = Modifier
          .padding(16.dp)
          .width(200.dp)
    ) {
      Text("Hello, World!")
    }
  }
}