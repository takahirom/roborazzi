package com.github.takahirom.roborazzi

import kotlinx.io.buffered
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.write

val Path.absolutePath: String
  get() = this.toString()

val Path.nameWithoutExtension: String
  get() = name.substringBeforeLast(".")

fun Path.relativeTo(base: Path): Path {
  if (this == base) return Path("")

  val thisSegments = this.absolutePath.segments()
  val baseSegments = base.absolutePath.segments()

  // Find the common prefix length
  var i = 0
  while (i < thisSegments.size && i < baseSegments.size && thisSegments[i] == baseSegments[i]) {
    i++
  }

  // Build the relative path by going back in base directory and adding remaining segments of this path
  val path = buildString {
    repeat(baseSegments.size - i) {
      append("..")
      append("/")
    }
    append(thisSegments.subList(i, thisSegments.size).joinToString("/") { it })
  }

  return Path(path)
}

private fun String.segments(): List<String> = split("/")

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