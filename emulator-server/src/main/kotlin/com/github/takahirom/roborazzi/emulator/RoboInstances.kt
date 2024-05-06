package com.github.takahirom.roborazzi.emulator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RoboInstances {
  suspend fun <T> executeOnMain(fn: suspend () -> T): T {
    println("${Thread.currentThread()} before executeOnMain")
    return withContext(Dispatchers.Main) {
      println("${Thread.currentThread()} executeOnMain")
      try {
        fn().also {
          println("${Thread.currentThread()} executeOnMain done")
        }
      } catch (e: Exception) {
        e.printStackTrace()
        throw e
      }
    }
  }

  suspend fun runWork() {
    // TODO contribute this thread per instance
  }
}