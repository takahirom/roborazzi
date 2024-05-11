package com.github.takahirom.roborazzi

interface TestNameExtractionStrategy {
  fun extract(): Pair<String, String>?
}
