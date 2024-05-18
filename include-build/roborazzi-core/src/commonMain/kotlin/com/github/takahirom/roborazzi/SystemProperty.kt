package com.github.takahirom.roborazzi

expect fun getSystemProperty(key: String): String?

fun getSystemProperty(key: String, default: String): String {
  return getSystemProperty(key) ?: default
}