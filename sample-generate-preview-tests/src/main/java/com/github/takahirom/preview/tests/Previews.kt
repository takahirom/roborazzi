package com.github.takahirom.preview.tests

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
    Surface(
    ) {
      ElevatedCard(
        Modifier
          .padding(8.dp)
          .width(180.dp),
        elevation = CardDefaults.elevatedCardElevation(
          defaultElevation = 12.dp
        )
      ) {
        Text(
          modifier = Modifier.padding(8.dp),
          text = "Generate Preview Test Sample"
        )
      }
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
  group = "Preview Group",
  locale = "ja-rJP",
  fontScale = 1.5f,
  widthDp = 320,
  heightDp = 640,
  // These properties are not supported by Roborazzi yet.
  apiLevel = 30
)
@Composable
fun PreviewWithProperties1() {
  Card(
    Modifier.width(100.dp)
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

@Preview(
  name = "Preview width & height large",
  widthDp = 2000,
  heightDp = 1000,
)
@Composable
fun PreviewWithWidthAndHeight() {
  Card(
    Modifier.fillMaxSize()
  ) {
    Text(
      modifier = Modifier.padding(8.dp),
      text = "Hello, World!"
    )
  }
}

@Preview(
  name = "Preview width & height",
  widthDp = 30,
  heightDp = 30,
)
@Composable
fun PreviewWithWidthAndHeightSmall() {
  Card(
    Modifier.fillMaxSize()
  ) {
    Text(
      modifier = Modifier.padding(8.dp),
      text = "Hello, World!"
    )
  }
}

@Preview(
  name = "Preview width",
  widthDp = 500,
  // These properties are not supported by Roborazzi yet.
  apiLevel = 30
)
@Composable
fun PreviewWithWidth() {
  Card(
    Modifier.fillMaxSize()
  ) {
    Text(
      modifier = Modifier.padding(8.dp),
      text = "Hello, World!"
    )
  }
}

@Preview(
  name = "Preview height",
  heightDp = 500,
)
@Composable
fun PreviewWithHeight() {
  Card(
    Modifier.fillMaxSize()
  ) {
    Text(
      modifier = Modifier.padding(8.dp),
      text = "Hello, World!"
    )
  }
}

@Preview(
  name = "Preview showBackground only",
  showBackground = true,
)
@Composable
fun PreviewShowBackgroundWithoutBackgroundColor() {
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
  name = "Preview showBackground & backgroundColor",
  showBackground = true,
  backgroundColor = 0xFF0000FF,
)
@Composable
fun PreviewShowBackgroundWithBackgroundColor() {
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