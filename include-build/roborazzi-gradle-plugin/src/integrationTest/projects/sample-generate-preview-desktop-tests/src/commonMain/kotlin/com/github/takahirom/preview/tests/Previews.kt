package com.github.takahirom.preview.tests

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.annotations.ManualClockOptions
import com.github.takahirom.roborazzi.annotations.RoboComposePreviewOptions
import com.github.takahirom.roborazzi.annotations.RoboPreviewExclude
import kotlinx.coroutines.delay

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

@RoboComposePreviewOptions(
  manualClockOptions = [
    ManualClockOptions(advanceTimeMillis = 0L),
    ManualClockOptions(advanceTimeMillis = 1032L),
  ]
)
@Preview
@Composable
fun PreviewDelayed() {
  var visible by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    delay(500)
    visible = true
  }
  MaterialTheme {
    if (visible) {
      Text(text = "Content appears after 500ms")
    } else {
      Text(text = "Waiting...")
    }
  }
}

@RoboPreviewExclude
@Preview
@Composable
fun PreviewExcluded() {
  MaterialTheme {
    Text(text = "This preview should be excluded")
  }
}

class NameProvider : PreviewParameterProvider<String> {
  override val values: Sequence<String> = sequenceOf("Takahiro", "Sergio")
}

@Preview
@Composable
fun PreviewWithParameter(@PreviewParameter(NameProvider::class) name: String) {
  MaterialTheme {
    Text(text = "Hello, $name!")
  }
}

// @Preview annotation options exercised by DesktopPreviewGenerateTest.

@Preview(widthDp = 300, heightDp = 150)
@Composable
fun PreviewFixedSize() {
  MaterialTheme {
    Text(
      modifier = Modifier.padding(8.dp),
      text = "Fixed size 300x150"
    )
  }
}

@Composable
private fun FontScaleSample() {
  MaterialTheme {
    Text(
      modifier = Modifier.padding(8.dp),
      text = "Font scale sample\nsecond line\nthird line"
    )
  }
}

@Preview
@Composable
fun PreviewFontScaleDefault() {
  FontScaleSample()
}

@Preview(fontScale = 2f)
@Composable
fun PreviewFontScaleLarge() {
  FontScaleSample()
}

@Preview(showBackground = true, backgroundColor = 0xFF0000FF)
@Composable
fun PreviewBackgroundBlue() {
  MaterialTheme {
    Text(
      modifier = Modifier.padding(16.dp),
      text = "Blue background"
    )
  }
}

@Composable
private fun UiModeSample() {
  val dark = isSystemInDarkTheme()
  Box(
    modifier = Modifier
      .size(100.dp)
      .background(if (dark) Color.Black else Color.White)
  )
}

@Preview
@Composable
fun PreviewUiModeLight() {
  UiModeSample()
}

// uiMode = 32 == android.content.res.Configuration.UI_MODE_NIGHT_YES (0x20).
@Preview(uiMode = 32)
@Composable
fun PreviewUiModeDark() {
  UiModeSample()
}

@Composable
private fun LocaleSample() {
  MaterialTheme {
    Text(
      modifier = Modifier.padding(8.dp),
      text = "locale=${Locale.current.toLanguageTag()}"
    )
  }
}

@Preview
@Composable
fun PreviewLocaleDefault() {
  LocaleSample()
}

@Preview(locale = "ja")
@Composable
fun PreviewLocaleJa() {
  LocaleSample()
}

class Filters {
  annotation class CustomExclude
}

@Filters.CustomExclude
@Preview
@Composable
fun PreviewExcludedByCustomAnnotation() {
  MaterialTheme {
    Text(text = "This preview should be excluded by the custom annotation")
  }
}
