package com.github.takahirom.preview.tests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter
import org.jetbrains.compose.ui.tooling.preview.PreviewParameterProvider

class StringProvider: PreviewParameterProvider<String> {
  override val values: Sequence<String> = sequenceOf("Takahiro", "Sergio")
}

@Preview
@Composable
fun PreviewNormal() {
  Text("Multiplatform Preview is working!")
}

@Preview
@Composable
fun PreviewParameter(@PreviewParameter(StringProvider::class) name: String) {
  Text("Multiplatform Preview with PreviewParameter is working, $name!")
}

@Preview(widthDp = 400, heightDp = 400, showBackground = true, backgroundColor = 0xFF0000FF)
@Composable
fun PreviewWithParameters() {
  Box(
    modifier = Modifier
      .size(400.dp)
      .background(Color(0xFF0000FF)),
    contentAlignment = Alignment.Center
  ) {
    Text(
      "Preview with custom parameters!",
      color = Color.White
    )
  }
}
