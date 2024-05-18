package com.github.takahirom.roborazzi

import kotlinx.io.buffered
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.write

val Path.absolutePath: String
  get() = this.toString()

fun Path.relativeTo(base: Path): Path {
  return Path(SystemFileSystem.resolve(base))
}

val Path.nameWithoutExtension: String
  get() = name.substringBeforeLast(".")

@OptIn(ExperimentalStdlibApi::class)
object KotlinxIo {


  fun readText(path: Path): String {

    return SystemFileSystem.source(path).buffered().use { source ->
      source.readString()
    }
  }

  fun writeText(path: Path, text: String) {
    SystemFileSystem.sink(path).buffered().use { sink ->
      sink.write(text.encodeToByteString())
    }
  }
}