package com.github.takahirom.roborazzi.emulator

import android.os.Looper
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@Config(sdk = [34], qualifiers = "w320dp-h533dp-normal-long-notround-any-hdpi-keyshidden-trackball")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class RoboEmulatorServerTest {
  @Test
  fun testOperations() = runTest {
    val server = GrpcServer()

    server.start()

    println(server.emulatorControllerService.getBattery(Unit))
    println(server.emulatorControllerService.getGps(Unit))

    server.close()
  }


  @Test
  fun testServer() {
    val server = GrpcServer()

    println("Starting on 8080")
    server.start()
    println("Started")

    val looper = Shadows.shadowOf(Looper.getMainLooper())
    while (true) {
      looper.idle()
    }
  }
}