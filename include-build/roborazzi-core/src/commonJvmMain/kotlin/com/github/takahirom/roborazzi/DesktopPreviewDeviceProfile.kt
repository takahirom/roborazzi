package com.github.takahirom.roborazzi

import java.io.Serializable

/**
 * How the Compose Desktop preview runtime sizes and scales a preview.
 *
 * A profile is a per-test-run decision, not a per-preview one: one module can capture the same
 * previews under several profiles by giving each its own Kotlin test run, which the Roborazzi
 * plugin turns into its own set of tasks and its own output directory.
 *
 * The presets are [Desktop] and [Pixel4a]. To vary a single axis, start from a preset and use
 * [copy]. There is deliberately no default: the profile decides the size and density of every
 * golden the module records, and no value is right for every project, so the build asks rather
 * than guessing.
 *
 * A profile fixes the device configuration - the surface size and the density - not the pixels.
 * Desktop measures text with the host OS font rather than the one Android ships, so a profile that
 * names an Android device still does not render text the way that device does, and the output is
 * expected to move as that fidelity improves. Such an improvement needs the goldens re-recorded,
 * the same way a Compose version bump already does.
 *
 * This type lives in `roborazzi-core` because the Gradle plugin and the desktop runtime both need
 * it, and the plugin must not depend on the Compose desktop scanner support module.
 */
@ExperimentalRoborazziApi
class DesktopPreviewDeviceProfile private constructor(
  /**
   * Device used to size previews that declare no `device`, written in the same grammar as
   * `@Preview(device = ...)` - `id:`, `name:` or `spec:`. It is parsed by ComposablePreviewScanner's
   * `DevicePreviewInfoParser`, so for example `"id:pixel_4a"` and
   * `"spec:width=411dp,height=891dp,dpi=420"` are both valid.
   *
   * `null` keeps the historical desktop behaviour: density is pinned at 1, the canvas is at least
   * 1024x768, `device` is ignored and only `widthDp`/`heightDp` affect the size.
   */
  val defaultDevice: String?,
) : Serializable {

  init {
    // `DevicePreviewInfoParser.parse("")` answers with its own default device rather than failing,
    // so a blank string would quietly mean something other than "no default device". `null` is the
    // only way to say that.
    require(defaultDevice == null || defaultDevice.isNotBlank()) {
      "Roborazzi: defaultDevice must be null or a device spec, not blank."
    }
    // The encoded profile travels on a forked JVM's command line, where a NUL or a newline is not
    // an argument the JVM can be handed. No device grammar contains one, so this only ever rejects
    // a string that was going to fail later and less clearly.
    require(defaultDevice == null || defaultDevice.none { it.isISOControl() }) {
      "Roborazzi: defaultDevice must not contain control characters, but was '$defaultDevice'."
    }
  }

  fun copy(
    defaultDevice: String? = this.defaultDevice,
  ): DesktopPreviewDeviceProfile = DesktopPreviewDeviceProfile(defaultDevice)

  /**
   * Serializes the profile so the Gradle plugin can hand it to the test JVM as a system property.
   *
   * [decode] reverses it. The encoded form is also what the plugin declares as a task input, so it
   * has to change whenever any axis changes.
   *
   * The result travels on a forked JVM's command line, so it is plain printable text: an axis left
   * at its default is an absent field rather than a marker value, and a value's own `;` and `\`
   * are escaped. It always starts with [VERSION] so that it is never empty - an empty system
   * property has to keep meaning "no profile configured".
   */
  fun encode(): String = buildString {
    append(VERSION)
    if (defaultDevice != null) {
      append(FIELD_SEPARATOR)
      append(KEY_DEFAULT_DEVICE)
      append('=')
      append(encodeValue(defaultDevice))
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DesktopPreviewDeviceProfile) return false
    return defaultDevice == other.defaultDevice
  }

  override fun hashCode(): Int = defaultDevice?.hashCode() ?: 0

  override fun toString(): String = "DesktopPreviewDeviceProfile(defaultDevice=$defaultDevice)"

  companion object {
    private const val serialVersionUID: Long = 1L

    private const val KEY_DEFAULT_DEVICE = "defaultDevice"
    private const val FIELD_SEPARATOR = ';'

    /**
     * Leads every encoded profile.
     *
     * It makes the encoding non-empty even for a profile whose every axis is at its default, and
     * gives a later release something to branch on if the format has to change.
     */
    private const val VERSION = "v1"

    /**
     * The historical desktop behaviour: fast, but sized in raw pixels at density 1 and blind to
     * `@Preview(device = ...)`.
     *
     * This is what the desktop runtime rendered before device profiles existed, so it is the
     * profile that leaves a project's existing goldens unchanged.
     */
    val Desktop: DesktopPreviewDeviceProfile = DesktopPreviewDeviceProfile(defaultDevice = null)

    /**
     * Sizes previews as a Pixel 4a, the device Roborazzi's Robolectric runtime defaults to, so the
     * same preview can be compared between the two runtimes.
     *
     * This is Roborazzi's own default device, not Android Studio's. Studio, and Google's Compose
     * Preview Screenshot Testing, preview a device they call Medium Phone: 1080x2400px at 420dpi,
     * against this profile's 1080x2340px at 440dpi. The density differs, so a preview captured
     * here is not comparable to a Studio preview.
     *
     * Written out as a spec rather than as `"id:pixel_4a"` because these are the dp that
     * `RobolectricDeviceQualifiers.Pixel4a` puts in the Robolectric configuration, and keeping
     * the two spelled the same way is what makes it obvious they have to change together. Both
     * spellings render the same 1080x2340 surface here.
     *
     * The Robolectric runtime captures two pixels narrower, 1078x2337. That is a Robolectric
     * inconsistency rather than a rule to reproduce: at these qualifiers its `Resources` and
     * `Configuration` report 1080x2340 while its `Display` reports a round-tripped 1078x2334, and
     * the activity window is laid out from the `Display`.
     *
     * What matches the Robolectric runtime here is the device configuration, not the text: see the
     * class documentation.
     */
    val Pixel4a: DesktopPreviewDeviceProfile =
      DesktopPreviewDeviceProfile(defaultDevice = "spec:width=393dp,height=851dp,dpi=440")

    /**
     * Spelled out for the message a build sees when it has configured no profile at all.
     *
     * Kept next to the presets so that adding one and forgetting to offer it here is a change to
     * this file rather than to a string somewhere in the Gradle plugin.
     */
    @InternalRoborazziApi
    val PRESET_CHOICES: String = """
      |  DesktopPreviewDeviceProfile.Desktop
      |      Density 1, so 1dp is 1px, on a surface of at least 1024x768, and
      |      @Preview(device = ...) is ignored. This is what the desktop runtime rendered
      |      before device profiles existed, so it leaves existing goldens unchanged.
      |
      |  DesktopPreviewDeviceProfile.Pixel4a
      |      393dp x 851dp at 440dpi, the device the Robolectric runtime defaults to, so the
      |      same preview can be compared between the two runtimes.
    """.trimMargin()

    fun decode(value: String): DesktopPreviewDeviceProfile {
      val parts = splitUnescaped(value).filter { it.isNotEmpty() }
      require(parts.firstOrNull() == VERSION) {
        "Malformed DesktopPreviewDeviceProfile: expected it to start with '$VERSION', but was '$value'"
      }
      val fields = parts.drop(1).associate { field ->
        val separator = field.indexOf('=')
        require(separator >= 0) { "Malformed DesktopPreviewDeviceProfile field: $field" }
        field.substring(0, separator) to field.substring(separator + 1)
      }
      return DesktopPreviewDeviceProfile(
        defaultDevice = fields[KEY_DEFAULT_DEVICE]?.let { decodeValue(it) },
      )
    }

    /** Splits on `;` while ignoring separators that [encodeValue] escaped. */
    private fun splitUnescaped(value: String): List<String> {
      val fields = mutableListOf<String>()
      val current = StringBuilder()
      var index = 0
      while (index < value.length) {
        val character = value[index]
        when {
          character == '\\' && index + 1 < value.length -> {
            current.append(character).append(value[index + 1])
            index += 2
          }

          character == ';' -> {
            fields.add(current.toString())
            current.clear()
            index++
          }

          else -> {
            current.append(character)
            index++
          }
        }
      }
      fields.add(current.toString())
      return fields
    }

    // Only the field separator and the escape character itself need escaping. `=` is safe because
    // decode splits each field at its FIRST `=`, and a device spec's own `=` all come after that.
    private fun encodeValue(value: String): String =
      value.replace("\\", "\\\\").replace(";", "\\;")

    private fun decodeValue(value: String): String =
      value.replace("\\;", ";").replace("\\\\", "\\")
  }
}

/**
 * The profile the Roborazzi Gradle plugin configured for this test run, or null when the build did
 * not set one.
 *
 * The plugin passes it as a system property so that several test runs of the same module can render
 * the same previews differently without recompiling anything.
 */
@ExperimentalRoborazziApi
fun roborazziSystemPropertyDesktopDeviceProfile(): DesktopPreviewDeviceProfile? =
  getSystemProperty(ROBORAZZI_DESKTOP_DEVICE_PROFILE_PROPERTY)
    ?.takeIf { it.isNotEmpty() }
    ?.let { DesktopPreviewDeviceProfile.decode(it) }

@ExperimentalRoborazziApi
const val ROBORAZZI_DESKTOP_DEVICE_PROFILE_PROPERTY: String = "roborazzi.desktop.deviceProfile"
