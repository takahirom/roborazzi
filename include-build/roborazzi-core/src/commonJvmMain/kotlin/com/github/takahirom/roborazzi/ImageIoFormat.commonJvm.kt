package com.github.takahirom.roborazzi

import java.awt.image.BufferedImage
import java.awt.image.RenderedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter
import javax.imageio.metadata.IIOMetadata
import javax.imageio.metadata.IIOMetadataFormatImpl
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.FileImageOutputStream

@ExperimentalRoborazziApi
@Suppress("FunctionName")
actual fun LosslessWebPImageIoFormat(): ImageIoFormat {
  return JvmImageIoFormat(
    awtImageWriter = losslessWebPWriter()
  )
}

@ExperimentalRoborazziApi
actual fun ImageIoFormat(): ImageIoFormat {
  return JvmImageIoFormat()
}

@ExperimentalRoborazziApi
fun interface AwtImageWriter {
  fun write(
    destFile: File,
    contextData: Map<String, Any>,
    image: BufferedImage
  )
}

@ExperimentalRoborazziApi
fun interface AwtImageLoader {
  fun load(inputFile: File): BufferedImage
}

@ExperimentalRoborazziApi
data class JvmImageIoFormat(
  val awtImageWriter: AwtImageWriter = AwtImageWriter { file, contextData, bufferedImage ->
    val imageExtension = file.extension.ifBlank { "png" }
    if (contextData.isEmpty()) {
      ImageIO.write(
        bufferedImage,
        imageExtension,
        file
      )
      return@AwtImageWriter
    }
    val writer = getWriter(bufferedImage, imageExtension)
    val meta = writer.writeMetadata(contextData, bufferedImage)
    writer.output = ImageIO.createImageOutputStream(file)
    writer.write(IIOImage(bufferedImage, null, meta))
  },
  val awtImageLoader: AwtImageLoader = AwtImageLoader { ImageIO.read(it) }
) : ImageIoFormat


@ExperimentalRoborazziApi
fun getWriter(renderedImage: RenderedImage, extension: String): ImageWriter {
  val typeSpecifier = ImageTypeSpecifier.createFromRenderedImage(renderedImage)
  val iterator: Iterator<*> = ImageIO.getImageWriters(typeSpecifier, extension)
  return if (iterator.hasNext()) {
    iterator.next() as ImageWriter
  } else {
    throw IllegalArgumentException("No ImageWriter found for $extension")
  }
}

@ExperimentalRoborazziApi
fun ImageWriter.writeMetadata(
  contextData: Map<String, Any>,
  bufferedImage: BufferedImage,
): IIOMetadata? {
  val meta = getDefaultImageMetadata(ImageTypeSpecifier(bufferedImage), null) ?: run {
    // If we use WebP, it seems that we can't get the metadata
    return null
  }

  val root = IIOMetadataNode(IIOMetadataFormatImpl.standardMetadataFormatName)
  contextData.forEach { (key, value) ->
    val textEntry = IIOMetadataNode("TextEntry")
    textEntry.setAttribute("keyword", key)
    textEntry.setAttribute("value", value.toString())
    val text = IIOMetadataNode("Text")
    text.appendChild(textEntry)
    root.appendChild(text)
  }

  meta.mergeTree(IIOMetadataFormatImpl.standardMetadataFormatName, root)
  return meta
}

@Suppress("UNCHECKED_CAST")
private fun getImageWriterFromSystemClassLoader(mimeType: String): ImageWriter {
  val systemCL = ClassLoader.getSystemClassLoader()
  val imageIOClass = systemCL.loadClass("javax.imageio.ImageIO")
  val method = imageIOClass.getMethod("getImageWritersByMIMEType", String::class.java)
  val iterator = method.invoke(null, mimeType) as Iterator<ImageWriter>
  return if (iterator.hasNext()) {
    iterator.next()
  } else {
    throw IllegalArgumentException("No ImageWriter found for MIME type: $mimeType")
  }
}

/**
 * Add testImplementation("io.github.darkxanter:webp-imageio:0.3.3") to use this
 */
private fun losslessWebPWriter(): AwtImageWriter =
  AwtImageWriter { file, context, bufferedImage ->
    // Use system classloader to avoid ClassCastException when running Robolectric tests
    // with multiple SDK configurations. Each SDK sandbox has its own classloader,
    // but by using system classloader, the WebPWriter is shared across all sandboxes.
    // See: https://github.com/takahirom/roborazzi/issues/771
    val writer: ImageWriter = getImageWriterFromSystemClassLoader("image/webp")
    try {
      val writeParam = writer.defaultWriteParam
      writeParam.compressionMode = ImageWriteParam.MODE_EXPLICIT
      val compressionTypes = writeParam.compressionTypes
      if (compressionTypes.isNullOrEmpty()) {
        throw IllegalStateException("No compression types available for WebP ImageWriter")
      }
      val losslessType = compressionTypes.firstOrNull {
        it.contains("Lossless", ignoreCase = true)
      } ?: compressionTypes[0]
      writeParam.compressionType = losslessType


      writer.setOutput(FileImageOutputStream(file))

      writer.write(null, IIOImage(bufferedImage, null, null), writeParam)
    } catch (e: NoClassDefFoundError) {
      throw IllegalStateException("Add testImplementation(\"io.github.darkxanter:webp-imageio:0.3.0\") to use this")
    } finally {
      writer.dispose()
    }
  }
