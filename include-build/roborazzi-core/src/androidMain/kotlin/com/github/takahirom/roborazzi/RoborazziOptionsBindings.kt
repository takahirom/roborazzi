package com.github.takahirom.roborazzi

import android.graphics.Bitmap

fun RoborazziOptions.PixelBitConfig.toBitmapConfig(): Bitmap.Config {
  return when (this) {
    RoborazziOptions.PixelBitConfig.Argb8888 -> Bitmap.Config.ARGB_8888
    RoborazziOptions.PixelBitConfig.Rgb565 -> Bitmap.Config.RGB_565
    else -> { Bitmap.Config.ARGB_8888 }
  }
}
