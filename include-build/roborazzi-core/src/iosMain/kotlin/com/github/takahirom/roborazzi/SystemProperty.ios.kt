package com.github.takahirom.roborazzi

import platform.Foundation.NSProcessInfo

actual fun getSystemProperty(key: String): String? {
  if (!NSProcessInfo.processInfo.environment.containsKey(key)){
    return null
  }
  return NSProcessInfo.processInfo.environment[key].toString()
}