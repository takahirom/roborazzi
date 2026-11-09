package io.github.takahirom.roborazzi

import com.github.takahirom.roborazzi.JvmImageIoFormat
import com.github.takahirom.roborazzi.getWriter
import com.github.takahirom.roborazzi.opaqueRgbPngImage
import com.github.takahirom.roborazzi.writeMetadata
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage
import java.awt.image.RenderedImage
import java.io.File
import java.util.Locale
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter
import javax.imageio.metadata.IIOMetadata
import javax.imageio.metadata.IIOMetadataFormatImpl
import javax.imageio.spi.IIORegistry
import javax.imageio.spi.ImageWriterSpi
import javax.imageio.stream.ImageOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.w3c.dom.Node

/** The opaque fast path preserves decoded pixels, caller storage, metadata, and provider fallback. */
class OpaquePngWriterTest {
  @Test
  fun opaqueArgbUsesRgbEncodingWithoutChangingPixels() {
    val image = opaqueImage(256, 257)
    val before = pixels(image)
    for (context in listOf(emptyMap(), mapOf("description" to "opaque screenshot", "count" to 7))) {
      withPngFile { file ->
        JvmImageIoFormat().awtImageWriter.write(file, context, image)
        assertEquals(2, pngColorType(file), "A fully opaque image should use RGB PNG encoding")
        assertPixelsEqual(image, ImageIO.read(file))
        assertContentEquals(before, pixels(image), "Writing must leave caller-owned pixels intact")
        assertEquals(context.mapValues { it.value.toString() }, textMetadata(file))
      }
    }
  }

  @Test
  fun rgbViewSharesExistingStorageWithoutChangingPixelsOrOwnership() {
    val image = opaqueImage(7, 9)
    val before = pixels(image)
    val writer = getWriter(image, "png")
    try {
      val view = assertNotNull(writer.opaqueRgbPngImage(image))
      assertEquals(BufferedImage.TYPE_INT_RGB, view.type)
      assertEquals(3, view.raster.numBands)
      assertFalse(view.colorModel.hasAlpha())
      assertSame(image.raster.dataBuffer, view.raster.dataBuffer)
      assertContentEquals(before, pixels(view))
      assertContentEquals(before, pixels(image), "Constructing the view must leave its input intact")
      view.flush()
      assertContentEquals(before, pixels(image), "The view does not own the shared pixel storage")
    } finally {
      writer.dispose()
    }
  }

  @Test
  fun rgbViewKeepsTheOriginalStockWriterEncodingParameters() {
    val image = opaqueImage(31, 33)
    val originalWriter = getWriter(image, "png")
    try {
      val view = assertNotNull(originalWriter.opaqueRgbPngImage(image))
      for (context in listOf(emptyMap(), mapOf("description" to "default encoding"))) {
        withPngFile { actual ->
          withPngFile { original ->
            JvmImageIoFormat().awtImageWriter.write(actual, context, image)
            writeOriginal(original, context, view)
            assertContentEquals(original.readBytes(), actual.readBytes())
          }
        }
      }
    } finally {
      originalWriter.dispose()
    }
  }

  @Test
  fun allTransparentAndMixedAlphaKeepOriginalBytes() {
    for (allTransparent in listOf(true, false)) {
      val image = opaqueImage(256, 3)
      for (y in 0 until image.height) {
        for (x in 0 until image.width) {
          val alpha = if (allTransparent) {
            0
          } else {
            x
          }
          image.setRGB(x, y, image.getRGB(x, y) and 0x00FFFFFF or (alpha shl 24))
        }
      }
      assertOriginalWritePath(image)
    }
  }

  @Test
  fun aSingleNonopaqueLastPixelKeepsOriginalBytes() {
    val image = opaqueImage(65, 67)
    image.setRGB(image.width - 1, image.height - 1, 0xFE123456.toInt())
    assertOriginalWritePath(image)
  }

  @Test
  fun otherImageLayoutsKeepOriginalBytes() {
    for (type in listOf(
      BufferedImage.TYPE_INT_RGB,
      BufferedImage.TYPE_INT_ARGB_PRE,
      BufferedImage.TYPE_4BYTE_ABGR,
      BufferedImage.TYPE_3BYTE_BGR,
      BufferedImage.TYPE_BYTE_GRAY,
    )) {
      val image = BufferedImage(31, 33, type)
      val source = opaqueImage(image.width, image.height)
      image.setRGB(0, 0, image.width, image.height, pixels(source), 0, image.width)
      assertOriginalWritePath(image)
    }
  }

  @Test
  fun opaqueSubimageIgnoresTransparentPixelsOutsideItsBounds() {
    val parent = BufferedImage(41, 39, BufferedImage.TYPE_INT_ARGB)
    val source = opaqueImage(13, 17)
    parent.setRGB(7, 9, source.width, source.height, pixels(source), 0, source.width)
    val image = parent.getSubimage(7, 9, source.width, source.height)
    val before = pixels(parent)
    withPngFile { file ->
      JvmImageIoFormat().awtImageWriter.write(file, emptyMap(), image)
      assertEquals(2, pngColorType(file))
      assertPixelsEqual(source, ImageIO.read(file))
      assertContentEquals(before, pixels(parent))
    }
  }

  @Test
  fun nonopaqueSubimageDoesNotReadAnOpaqueParentOrigin() {
    val parent = opaqueImage(41, 39)
    parent.setRGB(7 + 12, 9 + 16, 0x7F123456)
    assertOriginalWritePath(parent.getSubimage(7, 9, 13, 17))
  }

  @Test
  fun bilinearResizedOpaqueImageKeepsEveryDecodedPixel() {
    val source = opaqueImage(119, 83)
    val resized = BufferedImage(59, 41, BufferedImage.TYPE_INT_ARGB)
    AffineTransformOp(
      AffineTransform.getScaleInstance(0.5, 0.5),
      AffineTransformOp.TYPE_BILINEAR,
    ).filter(source, resized)
    withPngFile { file ->
      JvmImageIoFormat().awtImageWriter.write(file, emptyMap(), resized)
      assertEquals(2, pngColorType(file))
      assertPixelsEqual(resized, ImageIO.read(file))
    }
  }

  @Test
  fun optimizedWriteTruncatesExistingFilesWithAndWithoutMetadata() {
    for (context in listOf(emptyMap(), mapOf("description" to "small image"))) {
      withPngFile { overwritten ->
        withPngFile { fresh ->
          val format = JvmImageIoFormat()
          format.awtImageWriter.write(overwritten, context, opaqueImage(256, 257))
          val small = opaqueImage(7, 9)
          format.awtImageWriter.write(overwritten, context, small)
          format.awtImageWriter.write(fresh, context, small)
          assertContentEquals(fresh.readBytes(), overwritten.readBytes())
          assertPixelsEqual(small, ImageIO.read(overwritten))
        }
      }
    }
  }

  @Test
  fun noPngProviderLeavesTheExistingFileUntouched() {
    val registry = IIORegistry.getDefaultInstance()
    val providers = registry.getServiceProviders(ImageWriterSpi::class.java, true)
      .asSequence().filter { provider ->
        provider.formatNames.any { it.equals("png", ignoreCase = true) }
      }.toList()
    providers.forEach { registry.deregisterServiceProvider(it) }
    try {
      withPngFile { file ->
        val original = byteArrayOf(1, 2, 3, 4, 5)
        file.writeBytes(original)
        val format = JvmImageIoFormat()
        format.awtImageWriter.write(file, emptyMap(), opaqueImage(7, 9))
        assertContentEquals(original, file.readBytes())
        assertFailsWith<IllegalArgumentException> {
          format.awtImageWriter.write(file, mapOf("description" to "metadata"), opaqueImage(7, 9))
        }
        assertContentEquals(original, file.readBytes())
      }
    } finally {
      providers.forEach { registry.registerServiceProvider(it) }
    }
  }

  @Test
  fun thirdPartyPngProviderReceivesTheOriginalWriteContract() {
    val image = opaqueImage(17, 19)
    val stock = getWriter(image, "png")
    val stockProvider = stock.originatingProvider
    stock.dispose()
    val provider = RecordingPngProvider(stockProvider)
    val registry = IIORegistry.getDefaultInstance()
    registry.registerServiceProvider(provider)
    registry.setOrdering(ImageWriterSpi::class.java, provider, stockProvider)
    try {
      for (context in listOf(emptyMap(), mapOf("description" to "provider metadata"))) {
        withPngFile { file ->
          JvmImageIoFormat().awtImageWriter.write(file, context, image)
          assertEquals(6, pngColorType(file), "An unknown provider keeps its existing RGBA path")
          assertPixelsEqual(image, ImageIO.read(file))
          assertEquals(context, textMetadata(file))
        }
      }
      assertEquals(2, provider.createdWriters, "Each write should create exactly one provider instance")
      assertEquals(2, provider.disposedWriters)
      assertEquals(1, provider.renderedImageWrites, "Empty context keeps the RenderedImage write overload")
      assertEquals(2, provider.writeParams.size)
      assertTrue(provider.writeParams.all { it == null }, "Fallback must keep the original write parameters")
    } finally {
      registry.deregisterServiceProvider(provider)
    }
  }

  /** Compares the unchanged provider path byte-for-byte with the previous default writer behavior. */
  private fun assertOriginalWritePath(image: BufferedImage) {
    val writer = getWriter(image, "png")
    try {
      assertNull(writer.opaqueRgbPngImage(image))
    } finally {
      writer.dispose()
    }
    val before = pixels(image)
    for (context in listOf(emptyMap(), mapOf("description" to "fallback screenshot"))) {
      withPngFile { actual ->
        withPngFile { original ->
          JvmImageIoFormat().awtImageWriter.write(actual, context, image)
          writeOriginal(original, context, image)
          assertContentEquals(original.readBytes(), actual.readBytes())
          assertContentEquals(before, pixels(image))
          assertPixelsEqual(ImageIO.read(original), ImageIO.read(actual))
        }
      }
    }
  }

  /** Retains the prior empty-context ImageIO shortcut and explicit metadata write as the oracle. */
  private fun writeOriginal(file: File, context: Map<String, Any>, image: BufferedImage) {
    if (context.isEmpty()) {
      ImageIO.write(image, "png", file)
      return
    }
    val writer = getWriter(image, "png")
    try {
      val metadata = writer.writeMetadata(context, image)
      file.delete()
      ImageIO.createImageOutputStream(file).use { stream ->
        writer.output = stream
        writer.write(IIOImage(image, null, metadata))
      }
    } finally {
      writer.dispose()
    }
  }

  private fun opaqueImage(width: Int, height: Int): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until height) {
      for (x in 0 until width) {
        image.setRGB(x, y, 0xFF000000.toInt() or
          ((x and 255) shl 16) or ((y and 255) shl 8) or ((x * 17 + y * 31) and 255))
      }
    }
    return image
  }

  private fun pixels(image: BufferedImage): IntArray =
    image.getRGB(0, 0, image.width, image.height, null, 0, image.width)

  private fun assertPixelsEqual(expected: BufferedImage, actual: BufferedImage) {
    assertEquals(expected.width, actual.width)
    assertEquals(expected.height, actual.height)
    assertContentEquals(pixels(expected), pixels(actual))
  }

  private fun pngColorType(file: File): Int = file.readBytes()[25].toInt()

  private fun textMetadata(file: File): Map<String, String> {
    val values = mutableMapOf<String, String>()
    fun visit(node: Node) {
      if (node.nodeName == "TextEntry") {
        values[node.attributes.getNamedItem("keyword").nodeValue] =
          node.attributes.getNamedItem("value").nodeValue
      }
      for (index in 0 until node.childNodes.length) {
        visit(node.childNodes.item(index))
      }
    }
    ImageIO.createImageInputStream(file).use { input ->
      val reader = ImageIO.getImageReaders(input).next()
      try {
        reader.input = input
        visit(reader.getImageMetadata(0).getAsTree(IIOMetadataFormatImpl.standardMetadataFormatName))
      } finally {
        reader.dispose()
      }
    }
    return values
  }

  private fun withPngFile(block: (File) -> Unit) {
    val file = File.createTempFile("opaque-png", ".png")
    try {
      block(file)
    } finally {
      file.delete()
    }
  }

  /** A different provider delegates encoding while recording the public ImageWriter call it receives. */
  private class RecordingPngProvider(private val delegate: ImageWriterSpi) : ImageWriterSpi(
    "Roborazzi test", "1.0", arrayOf("png"), arrayOf("png"), arrayOf("image/png"),
    RecordingPngWriter::class.java.name, arrayOf(ImageOutputStream::class.java), null,
    false, null, null, null, null, true, null, null, null, null,
  ) {
    val writeParams = mutableListOf<ImageWriteParam?>()
    var createdWriters = 0
    var disposedWriters = 0
    var renderedImageWrites = 0

    override fun canEncodeImage(type: ImageTypeSpecifier): Boolean = delegate.canEncodeImage(type)

    override fun createWriterInstance(extension: Any?): ImageWriter {
      createdWriters++
      return RecordingPngWriter(this, delegate.createWriterInstance())
    }

    override fun getDescription(locale: Locale?): String = "Records PNG writer calls"
  }

  private class RecordingPngWriter(
    private val provider: RecordingPngProvider,
    private val delegate: ImageWriter,
  ) : ImageWriter(provider) {
    override fun getDefaultStreamMetadata(param: ImageWriteParam?): IIOMetadata? =
      delegate.getDefaultStreamMetadata(param)

    override fun getDefaultImageMetadata(type: ImageTypeSpecifier, param: ImageWriteParam?): IIOMetadata? =
      delegate.getDefaultImageMetadata(type, param)

    override fun convertStreamMetadata(metadata: IIOMetadata, param: ImageWriteParam?): IIOMetadata? =
      delegate.convertStreamMetadata(metadata, param)

    override fun convertImageMetadata(
      metadata: IIOMetadata,
      type: ImageTypeSpecifier,
      param: ImageWriteParam?,
    ): IIOMetadata? = delegate.convertImageMetadata(metadata, type, param)

    override fun write(image: RenderedImage) {
      provider.renderedImageWrites++
      super.write(image)
    }

    override fun write(metadata: IIOMetadata?, image: IIOImage, param: ImageWriteParam?) {
      provider.writeParams += param
      delegate.output = output
      delegate.write(metadata, image, param)
    }

    override fun dispose() {
      provider.disposedWriters++
      delegate.dispose()
      super.dispose()
    }
  }
}
