package com.github.takahirom.roborazzi.annotations

// TODO -> maybe add also parameter for ignoreFrames, as used in mainClock.advanceTime()
// TODO -> Docu: mention about the 16ms frame in Android

annotation class RoboComposePreviewOptions(
  val manualClockOptions: Long
) {

}


annotation class ManualClockOptions(
  val advanceTimeMillis: Long = 0L
)