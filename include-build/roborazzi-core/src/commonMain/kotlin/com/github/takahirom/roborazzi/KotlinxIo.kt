package com.github.takahirom.roborazzi

import kotlinx.io.buffered
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemPathSeparator
import kotlinx.io.readString
import kotlinx.io.write

val Path.absolutePath: String
  get() = this.toString()

val String.nameWithoutExtension: String
  get() = Path(this).name.substringBeforeLast(".")

val String.name: String
  get() = Path(this).name

fun Path.relativeTo(base: Path): Path {
  if (this == base) return Path("")

  val thisSegments = this.absolutePath.segments()
  val baseSegments = base.absolutePath.segments()
  println("Roborazzi DEBUG: thisSegments: $thisSegments")
  println("Roborazzi DEBUG: baseSegments: $baseSegments")

  // Find the common prefix length
  var i = 0
  while (i < thisSegments.size && i < baseSegments.size && thisSegments[i] == baseSegments[i]) {
    i++
  }
  println("Roborazzi DEBUG: $i")

  // Build the relative path by going back in base directory and adding remaining segments of this path
  val path = buildString {
    repeat(baseSegments.size - i) {
      append("..")
      append(SystemPathSeparator)
    }
    append(thisSegments.subList(i, thisSegments.size).joinToString(SystemPathSeparator.toString()) { it })
  }
  println("Roborazzi $path")

  return Path(path)
}

private fun String.segments(): List<String> = split(SystemPathSeparator)

@OptIn(ExperimentalStdlibApi::class)
object KotlinxIo {

  fun readText(path: String): String {
    return SystemFileSystem.source(Path(path)).buffered().use { source ->
      source.readString()
    }
  }

  fun writeText(path: String, text: String) {
    SystemFileSystem.sink(Path(path)).buffered().use { sink ->
      sink.write(text.encodeToByteString())
    }
  }
}