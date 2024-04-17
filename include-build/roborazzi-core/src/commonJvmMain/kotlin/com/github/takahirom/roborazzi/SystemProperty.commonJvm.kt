package com.github.takahirom.roborazzi

actual fun getSystemProperty(key: String): String? {
    return System.getProperty(key)
}