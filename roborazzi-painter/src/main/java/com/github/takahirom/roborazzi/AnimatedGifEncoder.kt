package com.github.takahirom.roborazzi

import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * From: http://www.java2s.com/Code/Java/2D-Graphics-GUI/AnimatedGifEncoder.htm
 * It is also used in Glide, Picasso which are famous image loading libraries
 *
 * Class AnimatedGifEncoder - Encodes a GIF file consisting of one or more
 * frames.
 *
 * <pre>
 * Example:
 * AnimatedGifEncoder e = new AnimatedGifEncoder();
 * e.start(outputFileName);
 * e.setDelay(1000);   // 1 frame per sec
 * e.addFrame(image1);
 * e.addFrame(image2);
 * e.finish();
</pre> *
 *
 * No copyright asserted on the source code of this class. May be used for any
 * purpose, however, refer to the Unisys LZW patent for restrictions on use of
 * the associated LZWEncoder class. Please forward any corrections to
 * kweiner@fmsware.com.
 *
 * @author Kevin Weiner, FM Software
 * @version 1.03 November 2003
 */
class AnimatedGifEncoder {
  private var width // image size
    = 0
  private var height = 0
  private var transparent: Color? = null // transparent color if given
  private var transIndex // transparent index in color table
    = 0
  private var repeat = -1 // no repeat
  private var delay = 0 // frame delay (hundredths)
  private var started = false // ready to output frames
  private var out: OutputStream? = null
  private var image // current frame
    : BufferedImage? = null
  private var pixels // BGR byte array from frame
    : ByteArray? = null
  private var indexedPixels // converted frame indexed to palette
    : ByteArray? =null
  private var colorDepth // number of bit planes
    = 0
  private var colorTab // RGB palette
    : ByteArray? = null
  private var usedEntry = BooleanArray(256) // active palette entries
  private var palSize = 7 // color table size (bits-1)
  private var dispose = -1 // disposal code (-1 = use default)
  private var closeStream = false // close stream when finished
  private var firstFrame = true
  private var sizeSet = false // if false, get size from first frame
  private var sample = 10 // default sample interval for quantizer

  /**
   * Sets the delay time between each frame, or changes it for subsequent frames
   * (applies to last frame added).
   *
   * @param ms
   * int delay time in milliseconds
   */
  fun setDelay(ms: Int) {
    delay = Math.round(ms / 10.0f)
  }

  /**
   * Sets the GIF frame disposal code for the last added frame and any
   * subsequent frames. Default is 0 if no transparent color has been set,
   * otherwise 2.
   *
   * @param code
   * int disposal code.
   */
  fun setDispose(code: Int) {
    if (code >= 0) {
      dispose = code
    }
  }

  /**
   * Sets the number of times the set of GIF frames should be played. Default is
   * 1; 0 means play indefinitely. Must be invoked before the first image is
   * added.
   *
   * @param iter
   * int number of iterations.
   * @return
   */
  fun setRepeat(iter: Int) {
    if (iter >= 0) {
      repeat = iter
    }
  }

  /**
   * Sets the transparent color for the last added frame and any subsequent
   * frames. Since all colors are subject to modification in the quantization
   * process, the color in the final palette for each frame closest to the given
   * color becomes the transparent color for that frame. May be set to null to
   * indicate no transparent color.
   *
   * @param c
   * Color to be treated as transparent on display.
   */
  fun setTransparent(c: Color?) {
    transparent = c
  }

  /**
   * Adds next GIF frame. The frame is not written immediately, but is actually
   * deferred until the next frame is received so that timing data can be
   * inserted. Invoking `finish()` flushes all frames. If
   * `setSize` was not invoked, the size of the first image is used
   * for all subsequent frames.
   *
   * @param im
   * BufferedImage containing frame to write.
   * @return true if successful.
   */
  fun addFrame(canvas: RoboCanvas): Boolean {
    val im = canvas.outputImage()
    if (im == null || !started) {
      return false
    }
    var ok = true
    try {
      if (!sizeSet) {
        // use first frame's size
        setSize(im.width, im.height)
      }
      image = im
      imagePixels // convert to correct format if necessary
      analyzePixels() // build color table & map pixels
      if (firstFrame) {
        writeLSD() // logical screen descriptior
        writePalette() // global color table
        if (repeat >= 0) {
          // use NS app extension to indicate reps
          writeNetscapeExt()
        }
      }
      writeGraphicCtrlExt() // write graphic control extension
      writeImageDesc() // image descriptor
      if (!firstFrame) {
        writePalette() // local color table
      }
      writePixels() // encode and write pixel data
      firstFrame = false
    } catch (e: IOException) {
      ok = false
    }
    return ok
  }

  /**
   * Flushes any pending data and closes output file. If writing to an
   * OutputStream, the stream is not closed.
   */
  fun finish(): Boolean {
    if (!started) {
      return false
    }
    var ok = true
    started = false
    try {
      out!!.write(0x3b) // gif trailer
      out!!.flush()
      if (closeStream) {
        out!!.close()
      }
    } catch (e: IOException) {
      ok = false
    }

    // reset for subsequent use
    transIndex = 0
    out = null
    image = null
    pixels = null
    indexedPixels = null
    colorTab = null
    closeStream = false
    firstFrame = true
    return ok
  }

  /**
   * Sets frame rate in frames per second. Equivalent to
   * `setDelay(1000/fps)`.
   *
   * @param fps
   * float frame rate (frames per second)
   */
  fun setFrameRate(fps: Float) {
    if (fps != 0f) {
      delay = Math.round(100f / fps)
    }
  }

  /**
   * Sets quality of color quantization (conversion of images to the maximum 256
   * colors allowed by the GIF specification). Lower values (minimum = 1)
   * produce better colors, but slow processing significantly. 10 is the
   * default, and produces good color mapping at reasonable speeds. Values
   * greater than 20 do not yield significant improvements in speed.
   *
   * @param quality
   * int greater than 0.
   * @return
   */
  fun setQuality(quality: Int) {
    var quality = quality
    if (quality < 1) {
      quality = 1
    }
    sample = quality
  }

  /**
   * Sets the GIF frame size. The default size is the size of the first frame
   * added if this method is not invoked.
   *
   * @param w
   * int frame width.
   * @param h
   * int frame width.
   */
  fun setSize(w: Int, h: Int) {
    if (started && !firstFrame) {
      return
    }
    width = w
    height = h
    if (width < 1) {
      width = 320
    }
    if (height < 1) {
      height = 240
    }
    sizeSet = true
  }

  /**
   * Initiates GIF file creation on the given stream. The stream is not closed
   * automatically.
   *
   * @param os
   * OutputStream on which GIF images are written.
   * @return false if initial write failed.
   */
  fun start(os: OutputStream?): Boolean {
    if (os == null) {
      return false
    }
    var ok = true
    closeStream = false
    out = os
    try {
      writeString("GIF89a") // header
    } catch (e: IOException) {
      ok = false
    }
    return ok.also { started = it }
  }

  /**
   * Initiates writing of a GIF file with the specified name.
   *
   * @param file
   * String containing output file name.
   * @return false if open or initial write failed.
   */
  fun start(file: String?): Boolean {
    var ok = true
    try {
      out = BufferedOutputStream(FileOutputStream(file))
      ok = start(out)
      closeStream = true
    } catch (e: IOException) {
      ok = false
    }
    return ok.also { started = it }
  }

  /**
   * Analyzes image colors and creates color map.
   */
  private fun analyzePixels() {
    val len = pixels!!.size
    val nPix = len / 3
    indexedPixels = ByteArray(nPix)
    val nq = NeuQuant(pixels, len, sample)
    // initialize quantizer
    colorTab = nq.process() // create reduced palette
    // convert map from BGR to RGB
    run {
      var i = 0
      while (i < colorTab!!.size) {
        val temp = colorTab!![i]
        colorTab!![i] = colorTab!![i + 2]
        colorTab!![i + 2] = temp
        usedEntry[i / 3] = false
        i += 3
      }
    }
    // map image pixels to new palette
    var k = 0
    for (i in 0 until nPix) {
      val index = nq.map(
        pixels!![k++].toInt() and 0xff,
        pixels!![k++].toInt() and 0xff,
        pixels!![k++].toInt() and 0xff
      )
      usedEntry[index] = true
      indexedPixels!![i] = index.toByte()
    }
    pixels = null
    colorDepth = 8
    palSize = 7
    // get closest match to transparent color if specified
    if (transparent != null) {
      transIndex = findClosest(transparent!!)
    }
  }

  /**
   * Returns index of palette color closest to c
   *
   */
  private fun findClosest(c: Color): Int {
    if (colorTab == null) {
      return -1
    }
    val r = c.red
    val g = c.green
    val b = c.blue
    var minpos = 0
    var dmin = 256 * 256 * 256
    val len = colorTab!!.size
    var i = 0
    while (i < len) {
      val dr = r - (colorTab!![i++].toInt() and 0xff)
      val dg = g - (colorTab!![i++].toInt() and 0xff)
      val db = b - (colorTab!![i].toInt() and 0xff)
      val d = dr * dr + dg * dg + db * db
      val index = i / 3
      if (usedEntry[index] && d < dmin) {
        dmin = d
        minpos = index
      }
      i++
    }
    return minpos
  }// create new image with right size/format

  /**
   * Extracts image pixels into byte array "pixels"
   */
  private val imagePixels: Unit
    private get() {
      val w = image!!.width
      val h = image!!.height
      val type = image!!.type
      if (w != width || h != height || type != BufferedImage.TYPE_3BYTE_BGR) {
        // create new image with right size/format
        val temp = BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR)
        val g = temp.createGraphics()
        g.drawImage(image, 0, 0, null)
        image = temp
      }
      pixels = (image!!.raster.dataBuffer as DataBufferByte).data
    }

  /**
   * Writes Graphic Control Extension
   */
  @Throws(IOException::class) private fun writeGraphicCtrlExt() {
    out!!.write(0x21) // extension introducer
    out!!.write(0xf9) // GCE label
    out!!.write(4) // data block size
    val transp: Int
    var disp: Int
    if (transparent == null) {
      transp = 0
      disp = 0 // dispose = no action
    } else {
      transp = 1
      disp = 2 // force clear if using transparent color
    }
    if (dispose >= 0) {
      disp = dispose and 7 // user override
    }
    disp = disp shl 2

    // packed fields
    out!!.write(
      0 or  // 1:3 reserved
        disp or  // 4:6 disposal
        0 or  // 7 user input - 0 = none
        transp
    ) // 8 transparency flag
    writeShort(delay) // delay x 1/100 sec
    out!!.write(transIndex) // transparent color index
    out!!.write(0) // block terminator
  }

  /**
   * Writes Image Descriptor
   */
  @Throws(IOException::class) private fun writeImageDesc() {
    out!!.write(0x2c) // image separator
    writeShort(0) // image position x,y = 0,0
    writeShort(0)
    writeShort(width) // image size
    writeShort(height)
    // packed fields
    if (firstFrame) {
      // no LCT - GCT is used for first (or only) frame
      out!!.write(0)
    } else {
      // specify normal LCT
      out!!.write(
        0x80 or  // 1 local color table 1=yes
          0 or  // 2 interlace - 0=no
          0 or  // 3 sorted - 0=no
          0 or  // 4-5 reserved
          palSize
      ) // 6-8 size of color table
    }
  }

  /**
   * Writes Logical Screen Descriptor
   */
  @Throws(IOException::class) private fun writeLSD() {
    // logical screen size
    writeShort(width)
    writeShort(height)
    // packed fields
    out!!.write(
      0x80 or  // 1 : global color table flag = 1 (gct used)
        0x70 or  // 2-4 : color resolution = 7
        0x00 or  // 5 : gct sort flag = 0
        palSize
    ) // 6-8 : gct size
    out!!.write(0) // background color index
    out!!.write(0) // pixel aspect ratio - assume 1:1
  }

  /**
   * Writes Netscape application extension to define repeat count.
   */
  @Throws(IOException::class) private fun writeNetscapeExt() {
    out!!.write(0x21) // extension introducer
    out!!.write(0xff) // app extension label
    out!!.write(11) // block size
    writeString("NETSCAPE" + "2.0") // app id + auth code
    out!!.write(3) // sub-block size
    out!!.write(1) // loop sub-block id
    writeShort(repeat) // loop count (extra iterations, 0=repeat forever)
    out!!.write(0) // block terminator
  }

  /**
   * Writes color table
   */
  @Throws(IOException::class) private fun writePalette() {
    out!!.write(colorTab, 0, colorTab!!.size)
    val n = 3 * 256 - colorTab!!.size
    for (i in 0 until n) {
      out!!.write(0)
    }
  }

  /**
   * Encodes and writes pixel data
   */
  @Throws(IOException::class) private fun writePixels() {
    val encoder = LZWEncoder(width, height, indexedPixels, colorDepth)
    encoder.encode(out)
  }

  /**
   * Write 16-bit value to output stream, LSB first
   */
  @Throws(IOException::class) private fun writeShort(value: Int) {
    out!!.write(value and 0xff)
    out!!.write(value shr 8 and 0xff)
  }

  /**
   * Writes string to output stream
   */
  @Throws(IOException::class) private fun writeString(s: String) {
    for (i in 0 until s.length) {
      out!!.write(s[i].code.toByte().toInt())
    }
  }
} /*
 * NeuQuant Neural-Net Quantization Algorithm
 * ------------------------------------------
 * 
 * Copyright (c) 1994 Anthony Dekker
 * 
 * NEUQUANT Neural-Net quantization algorithm by Anthony Dekker, 1994. See
 * "Kohonen neural networks for optimal colour quantization" in "Network:
 * Computation in Neural Systems" Vol. 5 (1994) pp 351-367. for a discussion of
 * the algorithm.
 * 
 * Any party obtaining a copy of these files from the author, directly or
 * indirectly, is granted, free of charge, a full and unrestricted irrevocable,
 * world-wide, paid up, royalty-free, nonexclusive right and license to deal in
 * this software and documentation files (the "Software"), including without
 * limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons who
 * receive copies from any such party to do so, with the only requirement being
 * that this copyright notice remain intact.
 */

// Ported to Java 12/00 K Weiner
internal class NeuQuant(thepic: ByteArray?, len: Int, sample: Int) {
  private var alphadec /* biased by 10 bits */ = 0

  /*
   * Types and Global Variables --------------------------
   */
  private var thepicture /* the input image itself */: ByteArray?
  private var lengthcount /* lengthcount = H*W*3 */: Int
  private var samplefac /* sampling factor 1..30 */: Int

  // typedef int pixel[4]; /* BGRc */
  private var network /* the network itself - [netsize][4] */: Array<IntArray?>
  private var netindex = IntArray(256)

  /* for network lookup - really 256 */
  private var bias = IntArray(netsize)

  /* bias and freq arrays for learning */
  private var freq = IntArray(netsize)
  private var radpower = IntArray(initrad)

  /* radpower for precomputation */ /*
   * Initialise network in range (0,0,0) to (255,255,255) and set parameters
   * -----------------------------------------------------------------------
   */
  init {
    var i: Int
    var p: IntArray?
    thepicture = thepic
    lengthcount = len
    samplefac = sample
    network = arrayOfNulls(netsize)
    i = 0
    while (i < netsize) {
      network[i] = IntArray(4)
      p = network[i]
      p!![2] = (i shl netbiasshift + 8) / netsize
      p[1] = p[2]
      p[0] = p[1]
      freq[i] = intbias / netsize /* 1/netsize */
      bias[i] = 0
      i++
    }
  }

  fun colorMap(): ByteArray {
    val map = ByteArray(3 * netsize)
    val index = IntArray(netsize)
    for (i in 0 until netsize) index[network[i]!![3]] = i
    var k = 0
    for (i in 0 until netsize) {
      val j = index[i]
      map[k++] = network[j]!![0].toByte()
      map[k++] = network[j]!![1].toByte()
      map[k++] = network[j]!![2].toByte()
    }
    return map
  }

  /*
   * Insertion sort of network and building of netindex[0..255] (to do after
   * unbias)
   * -------------------------------------------------------------------------------
   */
  fun inxbuild() {
    var i: Int
    var j: Int
    var smallpos: Int
    var smallval: Int
    var p: IntArray?
    var q: IntArray?
    var previouscol: Int
    var startpos: Int
    previouscol = 0
    startpos = 0
    i = 0
    while (i < netsize) {
      p = network[i]
      smallpos = i
      smallval = p!![1] /* index on g */
      /* find smallest in i..netsize-1 */j = i + 1
      while (j < netsize) {
        q = network[j]
        if (q!![1] < smallval) { /* index on g */
          smallpos = j
          smallval = q[1] /* index on g */
        }
        j++
      }
      q = network[smallpos]
      /* swap p (i) and q (smallpos) entries */if (i != smallpos) {
        j = q!![0]
        q[0] = p[0]
        p[0] = j
        j = q[1]
        q[1] = p[1]
        p[1] = j
        j = q[2]
        q[2] = p[2]
        p[2] = j
        j = q[3]
        q[3] = p[3]
        p[3] = j
      }
      /* smallval entry is now in position i */if (smallval != previouscol) {
        netindex[previouscol] = startpos + i shr 1
        j = previouscol + 1
        while (j < smallval) {
          netindex[j] = i
          j++
        }
        previouscol = smallval
        startpos = i
      }
      i++
    }
    netindex[previouscol] = startpos + maxnetpos shr 1
    j = previouscol + 1
    while (j < 256) {
      netindex[j] = maxnetpos /* really 256 */
      j++
    }
  }

  /*
   * Main Learning Loop ------------------
   */
  fun learn() {
    var i: Int
    var j: Int
    var b: Int
    var g: Int
    var r: Int
    var radius: Int
    var rad: Int
    var alpha: Int
    val step: Int
    var delta: Int
    val samplepixels: Int
    val p: ByteArray?
    var pix: Int
    val lim: Int
    if (lengthcount < minpicturebytes) {
      samplefac = 1
    }
    alphadec = 30 + (samplefac - 1) / 3
    p = thepicture
    pix = 0
    lim = lengthcount
    samplepixels = lengthcount / (3 * samplefac)
    delta = samplepixels / ncycles
    alpha = initalpha
    radius = initradius
    rad = radius shr radiusbiasshift
    if (rad <= 1) {
      rad = 0
    }
    i = 0
    while (i < rad) {
      radpower[i] = alpha * ((rad * rad - i * i) * radbias / (rad * rad))
      i++
    }

    // fprintf(stderr,"beginning 1D learning: initial radius=%d\n", rad);
    step = if (lengthcount < minpicturebytes) {
      3
    } else if (lengthcount % prime1 != 0) {
      3 * prime1
    } else {
      if (lengthcount % prime2 != 0) {
        3 * prime2
      } else {
        if (lengthcount % prime3 != 0) {
          3 * prime3
        } else {
          3 * prime4
        }
      }
    }
    i = 0
    while (i < samplepixels) {
      b = p!![pix + 0].toInt() and 0xff shl netbiasshift
      g = p[pix + 1].toInt() and 0xff shl netbiasshift
      r = p[pix + 2].toInt() and 0xff shl netbiasshift
      j = contest(b, g, r)
      altersingle(alpha, j, b, g, r)
      if (rad != 0) {
        alterneigh(rad, j, b, g, r) /* alter neighbours */
      }
      pix += step
      if (pix >= lim) {
        pix -= lengthcount
      }
      i++
      if (delta == 0) {
        delta = 1
      }
      if (i % delta == 0) {
        alpha -= alpha / alphadec
        radius -= radius / radiusdec
        rad = radius shr radiusbiasshift
        if (rad <= 1) {
          rad = 0
        }
        j = 0
        while (j < rad) {
          radpower[j] = alpha * ((rad * rad - j * j) * radbias / (rad * rad))
          j++
        }
      }
    }
    // fprintf(stderr,"finished 1D learning: final alpha=%f
    // !\n",((float)alpha)/initalpha);
  }

  /*
   * Search for BGR values 0..255 (after net is unbiased) and return colour
   * index
   * ----------------------------------------------------------------------------
   */
  fun map(b: Int, g: Int, r: Int): Int {
    var i: Int
    var j: Int
    var dist: Int
    var a: Int
    var bestd: Int
    var p: IntArray?
    var best: Int
    bestd = 1000 /* biggest possible dist is 256*3 */
    best = -1
    i = netindex[g] /* index on g */
    j = i - 1 /* start at netindex[g] and work outwards */
    while (i < netsize || j >= 0) {
      if (i < netsize) {
        p = network[i]
        dist = p!![1] - g /* inx key */
        if (dist >= bestd) {
          i = netsize /* stop iter */
        } else {
          i++
          if (dist < 0) {
            dist = -dist
          }
          a = p[0] - b
          if (a < 0) {
            a = -a
          }
          dist += a
          if (dist < bestd) {
            a = p[2] - r
            if (a < 0) {
              a = -a
            }
            dist += a
            if (dist < bestd) {
              bestd = dist
              best = p[3]
            }
          }
        }
      }
      if (j >= 0) {
        p = network[j]
        dist = g - p!![1] /* inx key - reverse dif */
        if (dist >= bestd) {
          j = -1 /* stop iter */
        } else {
          j--
          if (dist < 0) {
            dist = -dist
          }
          a = p[0] - b
          if (a < 0) {
            a = -a
          }
          dist += a
          if (dist < bestd) {
            a = p[2] - r
            if (a < 0) {
              a = -a
            }
            dist += a
            if (dist < bestd) {
              bestd = dist
              best = p[3]
            }
          }
        }
      }
    }
    return best
  }

  fun process(): ByteArray {
    learn()
    unbiasnet()
    inxbuild()
    return colorMap()
  }

  /*
   * Unbias network to give byte values 0..255 and record position i to prepare
   * for sort
   * -----------------------------------------------------------------------------------
   */
  fun unbiasnet() {
    var i: Int
    var j: Int
    i = 0
    while (i < netsize) {
      network[i]!![0] = network[i]!![0] shr netbiasshift
      network[i]!![1] = network[i]!![1] shr netbiasshift
      network[i]!![2] = network[i]!![2] shr netbiasshift
      network[i]!![3] = i /* record colour no */
      i++
    }
  }

  /*
   * Move adjacent neurons by precomputed alpha*(1-((i-j)^2/[r]^2)) in
   * radpower[|i-j|]
   * ---------------------------------------------------------------------------------
   */
  private fun alterneigh(rad: Int, i: Int, b: Int, g: Int, r: Int) {
    var j: Int
    var k: Int
    var lo: Int
    var hi: Int
    var a: Int
    var m: Int
    var p: IntArray?
    lo = i - rad
    if (lo < -1) {
      lo = -1
    }
    hi = i + rad
    if (hi > netsize) {
      hi = netsize
    }
    j = i + 1
    k = i - 1
    m = 1
    while (j < hi || k > lo) {
      a = radpower[m++]
      if (j < hi) {
        p = network[j++]
        try {
          p!![0] -= a * (p!![0] - b) / alpharadbias
          p[1] -= a * (p[1] - g) / alpharadbias
          p[2] -= a * (p[2] - r) / alpharadbias
        } catch (e: Exception) {
        } // prevents 1.3 miscompilation
      }
      if (k > lo) {
        p = network[k--]
        try {
          p!![0] -= a * (p!![0] - b) / alpharadbias
          p[1] -= a * (p[1] - g) / alpharadbias
          p[2] -= a * (p[2] - r) / alpharadbias
        } catch (e: Exception) {
        }
      }
    }
  }

  /*
   * Move neuron i towards biased (b,g,r) by factor alpha
   * ----------------------------------------------------
   */
  private fun altersingle(alpha: Int, i: Int, b: Int, g: Int, r: Int) {

    /* alter hit neuron */
    val n = network[i]
    n!![0] -= alpha * (n!![0] - b) / initalpha
    n[1] -= alpha * (n[1] - g) / initalpha
    n[2] -= alpha * (n[2] - r) / initalpha
  }

  /*
   * Search for biased BGR values ----------------------------
   */
  private fun contest(b: Int, g: Int, r: Int): Int {

    /* finds closest neuron (min dist) and updates freq */
    /* finds best neuron (min dist-bias) and returns position */
    /* for frequently chosen neurons, freq[i] is high and bias[i] is negative */
    /* bias[i] = gamma*((1/netsize)-freq[i]) */
    var i: Int
    var dist: Int
    var a: Int
    var biasdist: Int
    var betafreq: Int
    var bestpos: Int
    var bestbiaspos: Int
    var bestd: Int
    var bestbiasd: Int
    var n: IntArray?
    bestd = (1 shl 31).inv()
    bestbiasd = bestd
    bestpos = -1
    bestbiaspos = bestpos
    i = 0
    while (i < netsize) {
      n = network[i]
      dist = n!![0] - b
      if (dist < 0) {
        dist = -dist
      }
      a = n[1] - g
      if (a < 0) {
        a = -a
      }
      dist += a
      a = n[2] - r
      if (a < 0) {
        a = -a
      }
      dist += a
      if (dist < bestd) {
        bestd = dist
        bestpos = i
      }
      biasdist = dist - (bias[i] shr intbiasshift - netbiasshift)
      if (biasdist < bestbiasd) {
        bestbiasd = biasdist
        bestbiaspos = i
      }
      betafreq = freq[i] shr betashift
      freq[i] -= betafreq
      bias[i] += betafreq shl gammashift
      i++
    }
    freq[bestpos] += beta
    bias[bestpos] -= betagamma
    return bestbiaspos
  }

  companion object {
    private const val netsize = 256 /* number of colours used */

    /* four primes near 500 - assume no image has a length so large */ /* that it is divisible by all four primes */
    private const val prime1 = 499
    private const val prime2 = 491
    private const val prime3 = 487
    private const val prime4 = 503
    private const val minpicturebytes = 3 * prime4

    /* minimum size for input image */ /*
   * Program Skeleton ---------------- [select samplefac in range 1..30] [read
   * image from input file] pic = (unsigned char*) malloc(3*width*height);
   * initnet(pic,3*width*height,samplefac); learn(); unbiasnet(); [write output
   * image header, using writecolourmap(f)] inxbuild(); write output image using
   * inxsearch(b,g,r)
   */
    /*
   * Network Definitions -------------------
   */
    private const val maxnetpos = netsize - 1
    private const val netbiasshift = 4 /* bias for colour values */
    private const val ncycles = 100 /* no. of learning cycles */

    /* defs for freq and bias */
    private const val intbiasshift = 16 /* bias for fractions */
    private const val intbias = 1 shl intbiasshift
    private const val gammashift = 10 /* gamma = 1024 */
    private const val gamma = 1 shl gammashift
    private const val betashift = 10
    private const val beta = intbias shr betashift /* beta = 1/1024 */
    private const val betagamma = intbias shl gammashift - betashift

    /* defs for decreasing radius factor */
    private const val initrad = netsize shr 3 /*
                                                         * for 256 cols, radius
                                                         * starts
                                                         */
    private const val radiusbiasshift = 6 /* at 32.0 biased by 6 bits */
    private const val radiusbias = 1 shl radiusbiasshift
    private const val initradius = initrad * radiusbias /*
                                                                   * and
                                                                   * decreases
                                                                   * by a
                                                                   */
    private const val radiusdec = 30 /* factor of 1/30 each cycle */

    /* defs for decreasing alpha factor */
    private const val alphabiasshift = 10 /* alpha starts at 1.0 */
    private const val initalpha = 1 shl alphabiasshift

    /* radbias and alpharadbias used for radpower calculation */
    private const val radbiasshift = 8
    private const val radbias = 1 shl radbiasshift
    private const val alpharadbshift = alphabiasshift + radbiasshift
    private const val alpharadbias = 1 shl alpharadbshift
  }
} // ==============================================================================

// Adapted from Jef Poskanzer's Java port by way of J. M. G. Elliott.
// K Weiner 12/00
internal class LZWEncoder(
  private val imgW: Int,
  private val imgH: Int,
  private val pixAry: ByteArray?,
  color_depth: Int
) {
  private val initCodeSize: Int
  private var remaining = 0
  private var curPixel = 0

  // GIF Image compression - modified 'compress'
  //
  // Based on: compress.c - File compression ala IEEE Computer, June 1984.
  //
  // By Authors: Spencer W. Thomas (decvax!harpo!utah-cs!utah-gr!thomas)
  // Jim McKie (decvax!mcvax!jim)
  // Steve Davies (decvax!vax135!petsd!peora!srd)
  // Ken Turkowski (decvax!decwrl!turtlevax!ken)
  // James A. Woods (decvax!ihnp4!ames!jaw)
  // Joe Orost (decvax!vax135!petsd!joe)
  var n_bits // number of bits/code
    = 0
  var maxbits = BITS // user settable max # bits/code
  var maxcode // maximum code, given n_bits
    = 0
  var maxmaxcode = 1 shl BITS // should NEVER generate this code
  var htab = IntArray(HSIZE)
  var codetab = IntArray(HSIZE)
  var hsize = HSIZE // for dynamic table sizing
  var free_ent = 0 // first unused entry

  // block compression parameters -- after all codes are used up,
  // and compression rate changes, start over.
  var clear_flg = false

  // Algorithm: use open addressing double hashing (no chaining) on the
  // prefix code / next character combination. We do a variant of Knuth's
  // algorithm D (vol. 3, sec. 6.4) along with G. Knott's relatively-prime
  // secondary probe. Here, the modular division first probe is gives way
  // to a faster exclusive-or manipulation. Also do block compression with
  // an adaptive reset, whereby the code table is cleared when the compression
  // ratio decreases, but after the table fills. The variable-length output
  // codes are re-sized at this point, and a special CLEAR code is generated
  // for the decompressor. Late addition: construct the table according to
  // file size for noticeable speed improvement on small files. Please direct
  // questions about this implementation to ames!jaw.
  var g_init_bits = 0
  var ClearCode = 0
  var EOFCode = 0

  // output
  //
  // Output the given code.
  // Inputs:
  // code: A n_bits-bit integer. If == -1, then EOF. This assumes
  // that n_bits =< wordsize - 1.
  // Outputs:
  // Outputs code to the file.
  // Assumptions:
  // Chars are 8 bits long.
  // Algorithm:
  // Maintain a BITS character long buffer (so that 8 codes will
  // fit in it exactly). Use the VAX insv instruction to insert each
  // code in turn. When the buffer fills up empty it and start over.
  var cur_accum = 0
  var cur_bits = 0
  var masks = intArrayOf(
    0x0000, 0x0001, 0x0003, 0x0007, 0x000F, 0x001F, 0x003F, 0x007F, 0x00FF, 0x01FF,
    0x03FF, 0x07FF, 0x0FFF, 0x1FFF, 0x3FFF, 0x7FFF, 0xFFFF
  )

  // Number of characters so far in this 'packet'
  var a_count = 0

  // Define the storage for the packet accumulator
  var accum = ByteArray(256)

  // ----------------------------------------------------------------------------
  init {
    initCodeSize = Math.max(2, color_depth)
  }

  // Add a character to the end of the current packet, and if it is 254
  // characters, flush the packet to disk.
  @Throws(IOException::class) fun char_out(c: Byte, outs: OutputStream?) {
    accum[a_count++] = c
    if (a_count >= 254) {
      flush_char(outs)
    }
  }

  // Clear out the hash table
  // table clear for block compress
  @Throws(IOException::class) fun cl_block(outs: OutputStream?) {
    cl_hash(hsize)
    free_ent = ClearCode + 2
    clear_flg = true
    output(ClearCode, outs)
  }

  // reset code table
  fun cl_hash(hsize: Int) {
    for (i in 0 until hsize) htab[i] = -1
  }

  @Throws(IOException::class) fun compress(init_bits: Int, outs: OutputStream?) {
    var fcode: Int
    var i /* = 0 */: Int
    var c: Int
    var ent: Int
    var disp: Int
    val hsize_reg: Int
    var hshift: Int

    // Set up the globals: g_init_bits - initial number of bits
    g_init_bits = init_bits

    // Set up the necessary values
    clear_flg = false
    n_bits = g_init_bits
    maxcode = MAXCODE(n_bits)
    ClearCode = 1 shl init_bits - 1
    EOFCode = ClearCode + 1
    free_ent = ClearCode + 2
    a_count = 0 // clear packet
    ent = nextPixel()
    hshift = 0
    fcode = hsize
    while (fcode < 65536) {
      ++hshift
      fcode *= 2
    }
    hshift = 8 - hshift // set hash code range bound
    hsize_reg = hsize
    cl_hash(hsize_reg) // clear hash table
    output(ClearCode, outs)
    outer_loop@ while (nextPixel().also { c = it } != EOF) {
      fcode = (c shl maxbits) + ent
      i = c shl hshift xor ent // xor hashing
      if (htab[i] == fcode) {
        ent = codetab[i]
        continue
      } else if (htab[i] >= 0) // non-empty slot
      {
        disp = hsize_reg - i // secondary hash (after G. Knott)
        if (i == 0) {
          disp = 1
        }
        do {
          if (disp.let { i -= it; i } < 0) {
            i += hsize_reg
          }
          if (htab[i] == fcode) {
            ent = codetab[i]
            continue@outer_loop
          }
        } while (htab[i] >= 0)
      }
      output(ent, outs)
      ent = c
      if (free_ent < maxmaxcode) {
        codetab[i] = free_ent++ // code -> hashtable
        htab[i] = fcode
      } else {
        cl_block(outs)
      }
    }
    // Put out the final code.
    output(ent, outs)
    output(EOFCode, outs)
  }

  // ----------------------------------------------------------------------------
  @Throws(IOException::class) fun encode(os: OutputStream?) {
    os!!.write(initCodeSize) // write "initial code size" byte
    remaining = imgW * imgH // reset navigation variables
    curPixel = 0
    compress(initCodeSize + 1, os) // compress and write the pixel data
    os.write(0) // write block terminator
  }

  // Flush the packet to disk, and reset the accumulator
  @Throws(IOException::class) fun flush_char(outs: OutputStream?) {
    if (a_count > 0) {
      outs!!.write(a_count)
      outs.write(accum, 0, a_count)
      a_count = 0
    }
  }

  fun MAXCODE(n_bits: Int): Int {
    return (1 shl n_bits) - 1
  }

  // ----------------------------------------------------------------------------
  // Return the next pixel from the image
  // ----------------------------------------------------------------------------
  private fun nextPixel(): Int {
    if (remaining == 0) {
      return EOF
    }
    --remaining
    val pix = pixAry!![curPixel++]
    return pix.toInt() and 0xff
  }

  @Throws(IOException::class) fun output(code: Int, outs: OutputStream?) {
    cur_accum = cur_accum and masks[cur_bits]
    cur_accum = if (cur_bits > 0) {
      cur_accum or (code shl cur_bits)
    } else {
      code
    }
    cur_bits += n_bits
    while (cur_bits >= 8) {
      char_out((cur_accum and 0xff).toByte(), outs)
      cur_accum = cur_accum shr 8
      cur_bits -= 8
    }

    // If the next entry is going to be too big for the code size,
    // then increase it, if possible.
    if (free_ent > maxcode || clear_flg) {
      if (clear_flg) {
        maxcode = MAXCODE(g_init_bits.also { n_bits = it })
        clear_flg = false
      } else {
        ++n_bits
        maxcode = if (n_bits == maxbits) {
          maxmaxcode
        } else {
          MAXCODE(n_bits)
        }
      }
    }
    if (code == EOFCode) {
      // At EOF, write the rest of the buffer.
      while (cur_bits > 0) {
        char_out((cur_accum and 0xff).toByte(), outs)
        cur_accum = cur_accum shr 8
        cur_bits -= 8
      }
      flush_char(outs)
    }
  }

  companion object {
    private const val EOF = -1

    // GIFCOMPR.C - GIF Image compression routines
    //
    // Lempel-Ziv compression based on 'compress'. GIF modifications by
    // David Rowley (mgardi@watdcsu.waterloo.edu)
    // General DEFINEs
    const val BITS = 12
    const val HSIZE = 5003 // 80% occupancy
  }
}