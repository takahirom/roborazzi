package com.github.takahirom.preview.tests

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Wallpapers
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.annotations.ManualClockOptions
import com.github.takahirom.roborazzi.annotations.RoboComposePreviewOptions
import kotlinx.coroutines.delay

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

@Preview
@Composable
fun PreviewDialogSurface() {
  MaterialTheme {
    Surface {
      Box(Modifier.height(300.dp)) {
        Text("Hello, World!")
      }
      AlertDialog(
        onDismissRequest = {},
        confirmButton = @Composable { Text("Confirm") },
        text = @Composable { Text("Generate Preview Test Sample!") }
      )
    }
  }
}


@Preview(
  name = "Preview width and height large",
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
  name = "Preview width and height",
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
  name = "Preview width large",
  widthDp = 2000,
)
@Composable
fun PreviewWithWidthLarge() {
  Card(
    Modifier.fillMaxSize()
  ) {
    Text(
      modifier = Modifier.padding(8.dp),
      text = "Hello, World!2"
    )
  }
}

@Preview(
  name = "Preview height large",
  heightDp = 2000,
)
@Composable
fun PreviewWithHeightLarge() {
  Card(
    Modifier.fillMaxSize()
  ) {
    Text(
      modifier = Modifier.padding(8.dp),
      text = "Hello, World!2"
    )
  }
}

@Preview(
  name = "Preview width and height large pixel5",
  device = "spec:parent=pixel_5",
  widthDp = 2000,
  heightDp = 1000,
)
@Composable
fun PreviewWithWidthAndHeightPixel5() {
  Card(
    Modifier.fillMaxSize()
  ) {
    Text(
      modifier = Modifier.padding(8.dp),
      text = "Hello, World in portrait pixel 5"
    )
  }
}

@Preview(
  name = "Preview width and height large in portrait",
  device = "spec:parent=pixel_5,orientation=portrait",
  widthDp = 2000,
  heightDp = 1000,
)
@Composable
fun PreviewWithWidthAndHeightInportrait() {
  Card(
    Modifier.fillMaxSize()
  ) {
    Text(
      modifier = Modifier.padding(8.dp),
      text = "Hello, World in portrait"
    )
  }
}

@Preview(
  name = "Preview width and height large in landscape",
  device = "spec:parent=pixel_5,orientation=landscape",
  widthDp = 2000,
  heightDp = 1000,
)
@Composable
fun PreviewWithWidthAndHeightInLandscape() {
  Card(
    Modifier.fillMaxSize()
  ) {
    Text(
      modifier = Modifier.padding(8.dp),
      text = "Hello, World in Landscape!"
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
  name = "Preview showBackground and backgroundColor",
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

@RoboComposePreviewOptions
@Preview
@Composable
fun PreviewWithEmptyOptions() {
  Card(
    Modifier.fillMaxSize()
  ) {
    Text(
      modifier = Modifier.padding(8.dp),
      text = "Hello, World! Empty Options"
    )
  }
}

@RoboComposePreviewOptions(
  manualClockOptions = [
    ManualClockOptions(
      advanceTimeMillis = 0L,
    ),
    ManualClockOptions(
      advanceTimeMillis = 516L,
    ),
    ManualClockOptions(
      advanceTimeMillis = 1032L,
    ),
  ]
) // 500 ms + 16ms frame
@Preview
@Composable
fun PreviewDelayed() {
  var isBlue by remember { mutableStateOf(false) }
  var counter by remember { mutableStateOf(0) }

  // Trigger visibility change with a delay
  LaunchedEffect(Unit) {
    delay(500)
    isBlue = true
  }
  LaunchedEffect(Unit) {
    while (true) {
      delay(100)
      counter++
    }
  }

  Column(
    modifier = Modifier
      .size(300.dp)
      .background(if (isBlue) Color.Blue else Color.Gray)
  ) {
    Text("Counter: ${counter}00ms ")
    CircularProgressIndicator()
  }
}

// https://github.com/takahirom/roborazzi/issues/694
@Preview
@Composable
fun PreviewDialogSubcompose() {
  SubcomposeLayout { _ ->
    subcompose(Unit) {
      MaterialTheme {
        AlertDialog(
          onDismissRequest = {},
          confirmButton = @Composable { Text("Confirm") },
          text = @Composable { Text("Dialog wrapped by Subcompose") }
        )
      }
    }

    layout(0, 0) {}
  }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Preview
@Composable
fun PreviewDialogBoxWithConstraints() {
  BoxWithConstraints {
    MaterialTheme {
      AlertDialog(
        onDismissRequest = {},
        confirmButton = @Composable { Text("Confirm") },
        text = @Composable { Text("Dialog wrapped by BoxWithConstraints") }
      )
    }
  }
}