package com.github.takahirom.integration_test_project

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(
    text = "Hello $name!",
    style = MaterialTheme.typography.headlineLarge,
    modifier = modifier
  )
}
