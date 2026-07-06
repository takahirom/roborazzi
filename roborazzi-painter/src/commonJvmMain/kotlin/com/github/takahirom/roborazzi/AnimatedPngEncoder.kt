package com.github.takahirom.roborazzi

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import javax.imageio.ImageIO

/**
 * Minimal, dependency-free Animated PNG (APNG) encoder. Unlike GIF, APNG is lossless and full
 * color, so it faithfully preserves the captured pixels; this is also the groundwork for a future
 * animation compare/verify mode.
 *
 * Each frame is composited onto a fixed viewport (see [setSize]/[setBackground]) and encoded to a
 * regular PNG in memory with [ImageIO] eagerly in [addFrame]; the raw [BufferedImage] is dropped
 * immediately and only the (much smaller) compressed PNG bytes are retained until [finish]. This
 * keeps memory bounded by the encoded size rather than the raw bitmap size, similar to how the GIF
 * encoder streams. The frame count needed for the `acTL` chunk is only known at [finish], which is
 * why the encoded bytes (not the raw bitmaps) are held until then. Because every frame is encoded
 * the same way at the same dimensions, all frames share identical IHDR parameters. On [finish] the
 * frames are assembled into an APNG:
 *
 * - PNG signature + IHDR (from the first frame)
 * - `acTL` (frame count + number of plays, see [setRepeat])
 * - per frame an `fcTL`; the first frame's pixel data is written as `IDAT`, subsequent frames as
 *   `fdAT` (sequence-number-prefixed copies of their PNG image data)
 * - `IEND`
 *
 * Frame delays are uniform and expressed exactly as the fraction 1 / fps seconds (see
 * [setFrameRate]), so collecting frames and writing on [finish] is sufficient.
 */
@OptIn(ExperimentalRoborazziApi::class)
internal class AnimatedPngEncoder : AnimatedImageEncoder {
  private var out: OutputStream? = null
  private var width = 0
  private var height = 0
  private var sizeSet = false
  private var background: Color? = null
  // Frame delay expressed exactly as the fraction delay_num / delay_den seconds. Storing the fps
  // as the denominator (with a numerator of 1) avoids the rounding error of converting to whole
  // milliseconds first: e.g. 10 fps is exactly 1/10 s rather than 100/1000 s of a rounded value.
  private var delayDen = 10 // default 10 fps
  // Number of times the animation plays; mirrors GIF's setRepeat semantics (0 = loop forever).
  private var numPlays = 0
  // Each frame is encoded to compressed PNG bytes as soon as it is added, so only the encoded
  // payloads (not the raw bitmaps) accumulate here until finish().
  private val encodedFrames = mutableListOf<EncodedPng>()

  override fun start(output: OutputStream) {
    out = output
  }

  override fun setRepeat(iter: Int) {
    // Mirror GIF semantics: 0 = loop forever, N > 0 = play N times. Maps directly onto APNG's
    // acTL num_plays field, which uses the same convention.
    if (iter >= 0) {
      numPlays = iter
    }
  }

  override fun setFrameRate(fps: Float) {
    if (fps != 0f) {
      delayDen = Math.round(fps)
    }
  }

  override fun setBackground(argb: Int?) {
    background = argb?.let { Color(it, true) }
  }

  override fun setSize(width: Int, height: Int) {
    this.width = width
    this.height = height
    sizeSet = true
  }

  override fun addFrame(canvas: AwtRoboCanvas, resizeScale: Double) {
    val im = canvas.outputImage(resizeScale)
    if (!sizeSet) {
      setSize(im.width, im.height)
    }
    // Composite onto the fixed viewport anchored at the top-left, filling uncovered areas with the
    // background color so all frames share the same dimensions with no undefined (black) margins.
    val composited = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = composited.createGraphics()
    try {
      background?.let {
        g.color = it
        g.fillRect(0, 0, width, height)
      }
      g.drawImage(im, 0, 0, null)
    } finally {
      g.dispose()
    }
    // Encode to compressed PNG bytes now and drop the raw bitmap so it can be garbage collected.
    encodedFrames.add(encodePng(composited))
  }

  override fun finish() {
    val output = out ?: return
    try {
      if (encodedFrames.isNotEmpty()) {
        writeApng(output)
      }
      output.flush()
    } finally {
      out = null
      encodedFrames.clear()
    }
  }

  private fun writeApng(output: OutputStream) {
    val first = encodedFrames.first()

    output.write(PNG_SIGNATURE)
    // IHDR taken from the first frame; every frame uses identical parameters.
    writeChunk(output, "IHDR", first.ihdr)

    // acTL: number of frames + number of plays (0 = infinite; N > 0 = play N times).
    val actl = ByteArray(8)
    writeInt(actl, 0, encodedFrames.size)
    writeInt(actl, 4, numPlays)
    writeChunk(output, "acTL", actl)

    var sequenceNumber = 0
    encodedFrames.forEachIndexed { index, frame ->
      writeChunk(output, "fcTL", buildFctl(sequenceNumber++))
      if (index == 0) {
        writeChunk(output, "IDAT", frame.imageData)
      } else {
        // fdAT = sequence_number (4 bytes) + same deflate payload as an IDAT.
        val fdat = ByteArray(4 + frame.imageData.size)
        writeInt(fdat, 0, sequenceNumber++)
        System.arraycopy(frame.imageData, 0, fdat, 4, frame.imageData.size)
        writeChunk(output, "fdAT", fdat)
      }
    }
    writeChunk(output, "IEND", ByteArray(0))
  }

  private fun buildFctl(sequenceNumber: Int): ByteArray {
    val data = ByteArray(26)
    writeInt(data, 0, sequenceNumber)
    writeInt(data, 4, width)
    writeInt(data, 8, height)
    writeInt(data, 12, 0) // x_offset
    writeInt(data, 16, 0) // y_offset
    writeShort(data, 20, 1) // delay_num
    writeShort(data, 22, delayDen) // delay_den; delay is delay_num / delay_den seconds
    data[24] = 0 // dispose_op = APNG_DISPOSE_OP_NONE
    data[25] = 0 // blend_op = APNG_BLEND_OP_SOURCE
    return data
  }

  private class EncodedPng(val ihdr: ByteArray, val imageData: ByteArray)

  /**
   * Encodes [image] to a PNG with [ImageIO] and extracts its IHDR chunk data and the concatenated
   * IDAT chunk payloads.
   */
  private fun encodePng(image: BufferedImage): EncodedPng {
    val buffer = ByteArrayOutputStream()
    ImageIO.write(image, "png", buffer)
    val bytes = buffer.toByteArray()

    var ihdr: ByteArray? = null
    val idat = ByteArrayOutputStream()
    // Skip the 8-byte signature, then walk chunks: length(4) + type(4) + data + crc(4).
    var pos = PNG_SIGNATURE.size
    while (pos + 8 <= bytes.size) {
      val length = readInt(bytes, pos)
      val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
      val dataStart = pos + 8
      when (type) {
        "IHDR" -> ihdr = bytes.copyOfRange(dataStart, dataStart + length)
        "IDAT" -> idat.write(bytes, dataStart, length)
      }
      pos = dataStart + length + 4 // advance past data + CRC
      if (type == "IEND") break
    }
    return EncodedPng(
      ihdr = requireNotNull(ihdr) { "ImageIO produced a PNG without an IHDR chunk" },
      imageData = idat.toByteArray()
    )
  }

  private fun writeChunk(output: OutputStream, type: String, data: ByteArray) {
    val typeBytes = type.toByteArray(Charsets.US_ASCII)
    val lengthBytes = ByteArray(4)
    writeInt(lengthBytes, 0, data.size)
    output.write(lengthBytes)
    output.write(typeBytes)
    output.write(data)
    val crc = CRC32()
    crc.update(typeBytes)
    crc.update(data)
    val crcBytes = ByteArray(4)
    writeInt(crcBytes, 0, crc.value.toInt())
    output.write(crcBytes)
  }

  companion object {
    private val PNG_SIGNATURE = byteArrayOf(
      0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    private fun writeInt(target: ByteArray, offset: Int, value: Int) {
      target[offset] = (value ushr 24 and 0xff).toByte()
      target[offset + 1] = (value ushr 16 and 0xff).toByte()
      target[offset + 2] = (value ushr 8 and 0xff).toByte()
      target[offset + 3] = (value and 0xff).toByte()
    }

    private fun writeShort(target: ByteArray, offset: Int, value: Int) {
      target[offset] = (value ushr 8 and 0xff).toByte()
      target[offset + 1] = (value and 0xff).toByte()
    }

    private fun readInt(source: ByteArray, offset: Int): Int =
      (source[offset].toInt() and 0xff shl 24) or
        (source[offset + 1].toInt() and 0xff shl 16) or
        (source[offset + 2].toInt() and 0xff shl 8) or
        (source[offset + 3].toInt() and 0xff)
  }
}
