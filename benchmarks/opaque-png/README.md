# Opaque PNG benchmark

Build the JVM core:

```sh
./gradlew -p include-build :roborazzi-core:jvmJar
```

Set `PNG_CLASSPATH` to the resulting core JAR and the Kotlin standard library, separated by
your platform's classpath separator. Run with a full JDK 17 or later and a new output directory:

```sh
java -Xmx512m -XX:ActiveProcessorCount=2 -XX:+UseG1GC -Djava.awt.headless=true \
  --class-path "$PNG_CLASSPATH" benchmarks/opaque-png/SyntheticPngBenchmark.java png-results
```

Each writer saves the same 31 generated images. The baseline calls `ImageIO.write`. The candidate
calls Roborazzi's default writer without metadata. Every pass checks the decoded pixels and the
source arrays, including padding and subimage parents. A separate premultiplied image checks
rounding against the JDK writer.

`times.tsv` contains two warmup pairs and four measured pairs. The writers run in AB, BA, BA, AB
order for the measured pairs. Compare their median seconds and total PNG bytes. Only encoding
and file writes are timed. Rendering and image validation run outside the timer.
