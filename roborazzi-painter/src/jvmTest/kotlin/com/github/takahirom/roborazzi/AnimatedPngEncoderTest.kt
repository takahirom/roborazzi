package com.github.takahirom.roborazzi

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(InternalRoborazziApi::class)
class AnimatedPngEncoderTest {

  @Test
  fun encodesValidApngStructure() {
    val output = ByteArrayOutputStream()
    val encoder = AnimatedPngEncoder()
    encoder.start(output)
    encoder.setRepeat(2)
    encoder.setFrameDelayMillis(125)
    encoder.setSize(WIDTH, HEIGHT)
    encoder.setBackground(Color.WHITE.rgb)
    encoder.addFrame(frameCanvas(Color.RED), 1.0)
    encoder.addFrame(frameCanvas(Color.GREEN), 1.0)
    encoder.addFrame(frameCanvas(Color.BLUE), 1.0)
    encoder.finish()

    val bytes = output.toByteArray()
    assertTrue(
      bytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE),
      "output must start with the PNG signature"
    )
    val chunks = parseChunks(bytes)

    chunks.forEach { chunk ->
      assertTrue(chunk.crcValid, "CRC of ${chunk.type} chunk must be valid")
    }

    val types = chunks.map { it.type }
    assertEquals("IHDR", types.first(), "IHDR must be the first chunk")
    assertEquals("IEND", types.last(), "IEND must be the last chunk")
    val actlIndex = types.indexOf("acTL")
    assertTrue(actlIndex > types.indexOf("IHDR"), "acTL must come after IHDR")
    assertTrue(actlIndex < types.indexOf("IDAT"), "acTL must come before the first IDAT")

    val actl = chunks.single { it.type == "acTL" }
    assertEquals(3, readInt(actl.data, 0), "acTL num_frames")
    assertEquals(2, readInt(actl.data, 4), "acTL num_plays must match setRepeat")

    // The first frame's pixel data is a plain IDAT; subsequent frames are fdAT.
    assertEquals(3, types.count { it == "fcTL" }, "fcTL count")
    assertEquals(1, types.count { it == "IDAT" }, "IDAT count")
    assertEquals(2, types.count { it == "fdAT" }, "fdAT count")
    val frameDataTypes = types.filter { it == "IDAT" || it == "fdAT" }
    assertEquals(listOf("IDAT", "fdAT", "fdAT"), frameDataTypes)

    // Sequence numbers must be contiguous and ascending across fcTL and fdAT in stream order.
    val sequenceNumbers = chunks
      .filter { it.type == "fcTL" || it.type == "fdAT" }
      .map { readInt(it.data, 0) }
    assertEquals((0 until sequenceNumbers.size).toList(), sequenceNumbers)

    // Every fcTL frame region must fit within the IHDR canvas and carry the exact frame delay.
    val ihdr = chunks.single { it.type == "IHDR" }
    val canvasWidth = readInt(ihdr.data, 0)
    val canvasHeight = readInt(ihdr.data, 4)
    assertEquals(WIDTH, canvasWidth)
    assertEquals(HEIGHT, canvasHeight)
    chunks.filter { it.type == "fcTL" }.forEach { fctl ->
      val frameWidth = readInt(fctl.data, 4)
      val frameHeight = readInt(fctl.data, 8)
      val xOffset = readInt(fctl.data, 12)
      val yOffset = readInt(fctl.data, 16)
      assertTrue(
        xOffset + frameWidth <= canvasWidth && yOffset + frameHeight <= canvasHeight,
        "fcTL region ${frameWidth}x$frameHeight+$xOffset+$yOffset must fit within " +
          "${canvasWidth}x$canvasHeight"
      )
      assertEquals(125, readShort(fctl.data, 20), "fcTL delay_num")
      assertEquals(1000, readShort(fctl.data, 22), "fcTL delay_den")
    }
  }

  @Test
  fun addFrameBeforeStartThrows() {
    val encoder = AnimatedPngEncoder()
    assertFailsWith<IllegalStateException> {
      encoder.addFrame(frameCanvas(Color.RED), 1.0)
    }
  }

  @Test
  fun finishBeforeStartThrows() {
    val encoder = AnimatedPngEncoder()
    assertFailsWith<IllegalStateException> {
      encoder.finish()
    }
  }

  @Test
  fun setSizeAfterAddFrameThrows() {
    val encoder = AnimatedPngEncoder()
    encoder.start(ByteArrayOutputStream())
    encoder.addFrame(frameCanvas(Color.RED), 1.0)
    assertFailsWith<IllegalStateException> {
      encoder.setSize(WIDTH, HEIGHT)
    }
  }

  @Test
  fun setSizeWithNonPositiveDimensionsThrows() {
    val encoder = AnimatedPngEncoder()
    assertFailsWith<IllegalArgumentException> {
      encoder.setSize(0, HEIGHT)
    }
  }

  @Test
  fun instanceIsReusableAfterFinish() {
    val encoder = AnimatedPngEncoder()
    val firstOutput = ByteArrayOutputStream()
    encoder.start(firstOutput)
    encoder.addFrame(frameCanvas(Color.RED), 1.0)
    encoder.addFrame(frameCanvas(Color.GREEN), 1.0)
    encoder.finish()

    // A second cycle must not see the first cycle's frames or dimensions.
    val secondOutput = ByteArrayOutputStream()
    encoder.start(secondOutput)
    encoder.addFrame(frameCanvas(Color.BLUE, width = 4, height = 3), 1.0)
    encoder.finish()

    val chunks = parseChunks(secondOutput.toByteArray())
    val actl = chunks.single { it.type == "acTL" }
    assertEquals(1, readInt(actl.data, 0), "second cycle must contain only its own frame")
    val ihdr = chunks.single { it.type == "IHDR" }
    assertEquals(4, readInt(ihdr.data, 0), "second cycle must use its own width")
    assertEquals(3, readInt(ihdr.data, 4), "second cycle must use its own height")
  }

  private fun frameCanvas(color: Color, width: Int = WIDTH, height: Int = HEIGHT): AwtRoboCanvas {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
      graphics.color = color
      graphics.fillRect(0, 0, width, height)
    } finally {
      graphics.dispose()
    }
    val canvas = AwtRoboCanvas(
      width = width,
      height = height,
      filled = true,
      bufferedImageType = BufferedImage.TYPE_INT_ARGB
    )
    canvas.drawImage(image)
    return canvas
  }

  private class Chunk(val type: String, val data: ByteArray, val crcValid: Boolean)

  /** Walks the PNG chunk stream after the 8-byte signature, validating each chunk's CRC. */
  private fun parseChunks(bytes: ByteArray): List<Chunk> {
    val chunks = mutableListOf<Chunk>()
    var pos = PNG_SIGNATURE.size
    while (pos + 8 <= bytes.size) {
      val length = readInt(bytes, pos)
      val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
      val data = bytes.copyOfRange(pos + 8, pos + 8 + length)
      val expectedCrc = readInt(bytes, pos + 8 + length)
      val crc = CRC32()
      crc.update(bytes, pos + 4, 4 + length)
      chunks.add(Chunk(type, data, crc.value.toInt() == expectedCrc))
      pos += 12 + length
      if (type == "IEND") break
    }
    return chunks
  }

  private fun readInt(source: ByteArray, offset: Int): Int =
    (source[offset].toInt() and 0xff shl 24) or
      (source[offset + 1].toInt() and 0xff shl 16) or
      (source[offset + 2].toInt() and 0xff shl 8) or
      (source[offset + 3].toInt() and 0xff)

  private fun readShort(source: ByteArray, offset: Int): Int =
    (source[offset].toInt() and 0xff shl 8) or (source[offset + 1].toInt() and 0xff)

  companion object {
    private const val WIDTH = 8
    private const val HEIGHT = 6
    private val PNG_SIGNATURE = byteArrayOf(
      0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )
  }
}
