import com.github.takahirom.roborazzi.JvmImageIoFormat;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.io.PrintWriter;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;

/** Synthetic stock-PNG writer comparison. Corpus preparation and validation stay outside timers. */
public final class SyntheticPngBenchmark {
  private record Input(String name, BufferedImage root, BufferedImage image, Storage original) {}
  /** Preserve every backing-array element, including parent pixels outside a subimage. */
  private record Storage(DataBuffer identity, int[][] integers, byte[][] bytes) {
    static Storage snapshot(BufferedImage image) {
      DataBuffer buffer = image.getRaster().getDataBuffer();
      if (buffer instanceof DataBufferInt ints) {
        int[][] copy = Arrays.stream(ints.getBankData()).map(int[]::clone).toArray(int[][]::new);
        return new Storage(buffer, copy, null);
      } else if (buffer instanceof DataBufferByte values) {
        byte[][] copy = Arrays.stream(values.getBankData()).map(byte[]::clone).toArray(byte[][]::new);
        return new Storage(buffer, null, copy);
      } else {
        throw new IllegalArgumentException("Unsupported synthetic storage");
      }
    }

    void assertUnchanged(BufferedImage image) {
      DataBuffer actual = image.getRaster().getDataBuffer();
      if (actual != identity) {
        throw new AssertionError("Source backing buffer identity changed");
      }
      if (integers != null) {
        if (!Arrays.deepEquals(integers, ((DataBufferInt) actual).getBankData())) {
          throw new AssertionError("Source integer backing storage changed");
        }
      } else {
        if (!Arrays.deepEquals(bytes, ((DataBufferByte) actual).getBankData())) {
          throw new AssertionError("Source byte backing storage changed");
        }
      }
    }
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      throw new IllegalArgumentException("Usage: SyntheticPngBenchmark output-directory");
    }
    Path output = Files.createDirectory(Path.of(args[0]));
    Files.createDirectory(output.resolve("stock"));
    Files.createDirectory(output.resolve("candidate"));
    ImageIO.setUseCache(false);
    assertStockPngProvider();
    List<Input> corpus = corpus();
    JvmImageIoFormat format = new JvmImageIoFormat();
    System.out.printf("%s, %d images, %s%n", Runtime.version(), corpus.size(),
        JvmImageIoFormat.class.getProtectionDomain().getCodeSource().getLocation());
    var bean = ManagementFactory.getThreadMXBean();
    com.sun.management.ThreadMXBean allocation = bean instanceof com.sun.management.ThreadMXBean b ? b : null;
    if (allocation != null && allocation.isThreadAllocatedMemorySupported()) {
      allocation.setThreadAllocatedMemoryEnabled(true);
    } else {
      allocation = null;
    }
    checkPremultipliedOutput(output, format);
    // Alternate which writer goes first. Exclude the first two pairs from the comparison.
    boolean[] candidateFirst = {false, true, false, true, true, false};
    try (PrintWriter results = new PrintWriter(Files.newBufferedWriter(output.resolve("times.tsv")))) {
      results.println("phase\tpair\twriter\tseconds\tallocatedBytes\tpngBytes");
      for (int i = 0; i < candidateFirst.length; i++) {
        for (boolean candidate : new boolean[] {candidateFirst[i], !candidateFirst[i]}) {
          long before = allocation == null ? 0 : allocation.getCurrentThreadAllocatedBytes();
          long start = System.nanoTime();
          write(corpus, output, candidate, format);
          long elapsed = System.nanoTime() - start;
          long bytes = allocation == null ? -1 : allocation.getCurrentThreadAllocatedBytes() - before;
          long pngBytes = validate(corpus, output, candidate);
          results.printf(Locale.ROOT, "%s\t%d\t%s\t%.9f\t%d\t%d%n",
              i < 2 ? "warmup" : "measured", i < 2 ? i + 1 : i - 1,
              candidate ? "candidate" : "stock", elapsed / 1e9, bytes, pngBytes);
        }
      }
      if (results.checkError()) {
        throw new java.io.IOException("Failed to write benchmark results");
      }
    }
  }

  /** Premultiplication can round source colors. The decoded stock PNG is the reference here. */
  private static void checkPremultipliedOutput(Path output, JvmImageIoFormat format) throws Exception {
    List<Input> inputs = new ArrayList<>();
    add(inputs, "nonrepresentable-premultiplied", "gradient", 640, 480,
        BufferedImage.TYPE_INT_ARGB_PRE, "ramp", false);
    Input input = inputs.get(0);
    Path directory = output.resolve("premultiplied-oracle");
    Files.createDirectory(directory);
    Files.createDirectory(directory.resolve("stock"));
    Files.createDirectory(directory.resolve("candidate"));
    write(inputs, directory, false, format);
    input.original.assertUnchanged(input.root);
    BufferedImage stock = ImageIO.read(directory.resolve("stock/" + input.name + ".png").toFile());
    write(inputs, directory, true, format);
    input.original.assertUnchanged(input.root);
    BufferedImage candidate = ImageIO.read(directory.resolve("candidate/" + input.name + ".png").toFile());
    if (stock == null || candidate == null || stock.getWidth() != input.image.getWidth()
        || stock.getHeight() != input.image.getHeight() || candidate.getWidth() != stock.getWidth()
        || candidate.getHeight() != stock.getHeight()) {
      throw new AssertionError("Premultiplied oracle dimensions changed");
    }
    int width = stock.getWidth();
    int height = stock.getHeight();
    int[] expected = stock.getRGB(0, 0, width, height, null, 0, width);
    int[] actual = candidate.getRGB(0, 0, width, height, null, 0, width);
    if (!Arrays.equals(expected, actual)) {
      throw new AssertionError("Candidate differs from stock premultiplied decoding");
    }
    stock.flush();
    candidate.flush();
  }

  /** Match the released empty-context default call while invoking the real candidate writer. */
  private static void write(List<Input> corpus, Path output, boolean candidate, JvmImageIoFormat format)
      throws Exception {
    for (Input input : corpus) {
      File file = output.resolve(candidate ? "candidate" : "stock").resolve(input.name + ".png").toFile();
      if (candidate) {
        format.getAwtImageWriter().write(file, Map.of(), input.image);
      } else {
        if (!ImageIO.write(input.image, "png", file)) {
          throw new AssertionError("PNG writer missing");
        }
      }
    }
  }

  /** Check decoded pixels and source storage outside the timed write. */
  private static long validate(List<Input> corpus, Path output, boolean candidate) throws Exception {
    long bytes = 0;
    for (Input input : corpus) {
      input.original.assertUnchanged(input.root);
      Path file = output.resolve(candidate ? "candidate" : "stock").resolve(input.name + ".png");
      BufferedImage decoded = ImageIO.read(file.toFile());
      if (decoded == null || decoded.getWidth() != input.image.getWidth() || decoded.getHeight() != input.image.getHeight()) {
        throw new AssertionError("Output dimensions changed: " + input.name);
      }
      int width = decoded.getWidth();
      int height = decoded.getHeight();
      int[] expected = input.image.getRGB(0, 0, width, height, null, 0, width);
      int[] actual = decoded.getRGB(0, 0, width, height, null, 0, width);
      if (!Arrays.equals(expected, actual)) {
        throw new AssertionError("Decoded RGBA changed: " + input.name);
      }
      decoded.flush();
      bytes += Files.size(file);
    }
    return bytes;
  }

  /** Refuse plugin-provider ambiguity so this probe describes the guarded stock-writer path. */
  private static void assertStockPngProvider() {
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
    int count = 0;
    while (writers.hasNext()) {
      ImageWriter writer = writers.next();
      try {
        if (!writer.getClass().getName().equals("com.sun.imageio.plugins.png.PNGImageWriter")
            || writer.getClass().getClassLoader() != null || writer.getOriginatingProvider() == null
            || writer.getOriginatingProvider().getClass().getClassLoader() != null) {
          throw new IllegalStateException("A non-stock PNG provider is registered");
        }
        count++;
      } finally {
        writer.dispose();
      }
    }
    if (count != 1) {
      throw new IllegalStateException("Expected exactly one stock PNG writer");
    }
  }

  /** Use integer-only deterministic patterns, with no fonts, downloaded assets or app content. */
  private static List<Input> corpus() {
    List<Input> result = new ArrayList<>();
    int[][] sizes = {{17, 13}, {256, 256}, {461, 1000}, {1000, 625}, {1920, 1080}};
    for (String pattern : List.of("flat", "cards", "gradient", "noise")) {
      for (int[] size : sizes) {
        add(result, pattern + "-" + size[0] + "x" + size[1], pattern,
            size[0], size[1], BufferedImage.TYPE_INT_ARGB, "opaque", false);
      }
    }
    add(result, "alpha-gradient", "gradient", 640, 480, BufferedImage.TYPE_INT_ARGB, "ramp", false);
    add(result, "alpha-noise", "noise", 640, 480, BufferedImage.TYPE_INT_ARGB, "ramp", false);
    add(result, "transparent-colored-rgb", "cards", 640, 480, BufferedImage.TYPE_INT_ARGB, "zero", false);
    add(result, "single-alpha-last-pixel", "gradient", 640, 480, BufferedImage.TYPE_INT_ARGB, "last", false);
    add(result, "opaque-subimage-alpha-parent", "cards", 477, 299, BufferedImage.TYPE_INT_ARGB, "opaque", true);
    add(result, "alpha-subimage", "gradient", 477, 299, BufferedImage.TYPE_INT_ARGB, "ramp", true);
    add(result, "opaque-premultiplied", "cards", 640, 480, BufferedImage.TYPE_INT_ARGB_PRE, "opaque", false);
    add(result, "alpha-premultiplied", "representable", 640, 480, BufferedImage.TYPE_INT_ARGB_PRE, "ramp", false);
    add(result, "rgb-storage", "cards", 640, 480, BufferedImage.TYPE_INT_RGB, "opaque", false);
    add(result, "bgr-byte-storage", "gradient", 640, 480, BufferedImage.TYPE_3BYTE_BGR, "opaque", false);
    add(result, "abgr-byte-alpha", "cards", 640, 480, BufferedImage.TYPE_4BYTE_ABGR, "ramp", false);
    return result;
  }

  /** Child images retain a larger parent row stride and nonzero translation. */
  private static void add(List<Input> result, String name, String pattern, int width, int height,
      int type, String alpha, boolean child) {
    BufferedImage root = new BufferedImage(width + (child ? 29 : 0), height + (child ? 23 : 0), type);
    if (child) {
      for (int y = 0; y < root.getHeight(); y++) {
        for (int x = 0; x < root.getWidth(); x++) {
          root.setRGB(x, y, 0x4000aa77); // Outside the visible opaque child remains translucent.
        }
      }
    }
    BufferedImage image = child ? root.getSubimage(11, 7, width, height) : root;
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int value = color(pattern, x, y, width, height);
        int a;
        if (alpha.equals("ramp")) {
          a = (x * 17 + y * 31) & 255;
        } else if (alpha.equals("zero")) {
          a = 0;
        } else if (alpha.equals("last") && x == width - 1 && y == height - 1) {
          a = 254;
        } else {
          a = 255;
        }
        image.setRGB(x, y, (a << 24) | value);
      }
    }
    result.add(new Input(name, root, image, Storage.snapshot(root)));
  }

  private static int color(String pattern, int x, int y, int width, int height) {
    if (pattern.equals("representable")) {
      // Binary color channels remain exact under every nonzero alpha multiplier.
      return ((x & 1) == 0 ? 0xff0000 : 0) | ((y & 1) == 0 ? 0x00ff00 : 0)
          | (((x + y) & 2) == 0 ? 0x0000ff : 0);
    } else if (pattern.equals("flat")) {
      return 0xe8edf3;
    } else if (pattern.equals("gradient")) {
      int red = x * 255 / Math.max(1, width - 1);
      int green = y * 255 / Math.max(1, height - 1);
      return (red << 16) | (green << 8) | ((red + green) / 2);
    } else if (pattern.equals("noise")) {
      int value = x * 0x1f123bb5 + y * 0x5f356495 + 0x6d2b79f5;
      value ^= value >>> 16;
      value *= 0x45d9f3b;
      value ^= value >>> 16;
      return value & 0xffffff;
    } else {
      int cellX = x % 120;
      int cellY = y % 100;
      if (cellX >= 12 && cellX < 106 && cellY >= 12 && cellY < 84) {
        if (cellY >= 27 && cellY < 31 && cellX >= 24 && cellX < 88) {
          return 0x475569;
        } else if (cellY >= 44 && cellY < 48 && cellX >= 24 && cellX < 76) {
          return 0xaabbcc;
        } else {
          return 0xffffff;
        }
      } else if (cellX >= 9 && cellX < 111 && cellY >= 9 && cellY < 91) {
        return 0xe1e4e8;
      } else {
        return 0xf3f5f8;
      }
    }
  }
}
