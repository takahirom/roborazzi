package com.github.takahirom.roborazzi

import dev.shreyaspatil.ai.client.generativeai.type.PlatformImage
import java.io.File

internal actual fun readByteArrayFromFile(filePath: String): PlatformImage {
  return PlatformImage(File(filePath).readBytes())
}