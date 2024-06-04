package com.github.takahirom.roborazzi.emulator

import com.android.emulator.control.EmulatorControllerClient
import com.android.emulator.control.ImageFormat
import com.squareup.wire.GrpcClient
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okio.FileSystem
import okio.Path.Companion.toPath
import roborazzi.emulator.ComposePreview
import roborazzi.emulator.RoborazziClient

suspend fun main() {
  val port = 8080
  val grpcClient = GrpcClient.Builder()
        .client(
          OkHttpClient.Builder()
          .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
          .build())
        .baseUrl("http://localhost:$port")
        .build()

  val roborazziService = grpcClient.create(RoborazziClient::class)
  val emulatorControllerClient = grpcClient.create(EmulatorControllerClient::class)

  val metadata = mapOf("instance" to "1")

  println("--> getGps")
  val gpsCall = emulatorControllerClient.getGps()
  gpsCall.requestMetadata = metadata
  println(gpsCall.execute(Unit))

  println("--> launchComposePreview")
  val previewCall = roborazziService.launchComposePreview()
  previewCall.requestMetadata = metadata
  previewCall.execute(ComposePreview(previewMethod = "com.github.takahirom.roborazzi.emulator.previews.SimplePreviewKt.SimplePreview"))

  println("--> getScreenshot")
  val screenshotCall = emulatorControllerClient.getScreenshot()
  screenshotCall.requestMetadata = metadata
  val image = screenshotCall.execute(ImageFormat())
  println(image)
  val path = "emulator-client/build/SimplePreview.png".toPath()
  FileSystem.SYSTEM.write(path) {
    write(image.image)
  }
  println("Wrote: $path")
}