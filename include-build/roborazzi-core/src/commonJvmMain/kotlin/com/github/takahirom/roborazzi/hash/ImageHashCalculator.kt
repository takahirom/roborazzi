package com.github.takahirom.roborazzi.hash

import com.dropbox.differ.Image

interface ImageHashCalculator {

  interface HashResult {
    abstract fun toBytes(): ByteArray
  }

  fun hash(image: Image): HashResult

  fun load(bytes: ByteArray): HashResult

  fun extension(): String
  fun areSimilar(hashResultA: HashResult, hashResultB: HashResult): Boolean =
    hashResultA == hashResultB
}
