package com.github.takahirom.roborazzi

@InternalRoborazziApi
fun roborazziReportLog(message: String) {
  println("Roborazzi: $message")
}

@InternalRoborazziApi
expect fun roborazziErrorLog(message: String)