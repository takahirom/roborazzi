package com.github.takahirom.roborazzi

import dev.shreyaspatil.ai.client.generativeai.type.PlatformImage
import dev.shreyaspatil.ai.client.generativeai.type.asPlatformImage
import platform.UIKit.UIImage

actual fun readByteArrayFromFile(filePath: String): PlatformImage {
  return UIImage.imageWithContentsOfFile(filePath)!!.asPlatformImage()
}