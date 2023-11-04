package com.github.takahirom.roborazzi.hash

import com.dropbox.differ.Image
import korlibs.crypto.md5

class Md5ImageHashCalculator : ImageHashCalculator {

  data class Md5HashResult(private val hash: ByteArray) : ImageHashCalculator.HashResult {
    override fun toBytes(): ByteArray {
      return hash
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Md5HashResult

      return hash.contentEquals(other.hash)
    }

    override fun hashCode(): Int {
      return hash.contentHashCode()
    }
  }

  override fun hash(image: Image): ImageHashCalculator.HashResult {
    val byteArray = ByteArray(image.width * image.height * 4)
    for (y in 0 until image.height) {
      for (x in 0 until image.width) {
        val color = image.getPixel(x, y)
        val index = (y * image.width + x) * 4
        byteArray[index] = (color.r * 255).toInt().toByte()
        byteArray[index + 1] = (color.g * 255).toInt().toByte()
        byteArray[index + 2] = (color.b * 255).toInt().toByte()
        byteArray[index + 3] = (color.a * 255).toInt().toByte()
      }
    }
    return Md5HashResult(byteArray.md5().bytes)
  }

  override fun load(bytes: ByteArray): ImageHashCalculator.HashResult {
    return Md5HashResult(bytes)
  }

  override fun extension(): String {
    return "md5"
  }
}