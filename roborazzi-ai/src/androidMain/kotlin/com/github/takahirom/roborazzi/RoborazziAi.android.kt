package com.github.takahirom.roborazzi

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.shreyaspatil.ai.client.generativeai.type.PlatformImage
import java.io.ByteArrayOutputStream

actual fun readByteArrayFromFile(filePath: String): PlatformImage {
  val bitmap = BitmapFactory.decodeFile(filePath)
  val outputStream = ByteArrayOutputStream()
  val data = outputStream.use {
    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it)
    outputStream.toByteArray()
  }
  return PlatformImage(data = data)
}