package com.github.takahirom.roborazzi.annotations

// TODO -> maybe add also parameter for ignoreFrames, as used in mainClock.advanceTime()
// TODO -> Make BINARY annotation only applicable to Methods
// TODO -> Docu: mention about the 16ms frame in Android
annotation class DelayCaptureRoboImage(val delayInMillis: Long)