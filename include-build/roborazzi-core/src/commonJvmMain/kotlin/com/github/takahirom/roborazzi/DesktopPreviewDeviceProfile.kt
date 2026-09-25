package com.github.takahirom.roborazzi

import java.io.Serializable

/**
 * How the Compose Desktop preview runtime sizes and scales a preview.
 *
 * A profile is a per-test-run decision, not a per-preview one: one module can capture the same
 * previews under several profiles by giving each its own Kotlin test run, which the Roborazzi
 * plugin turns into its own set of tasks and its own output directory.
 *
 * The presets are [Desktop] and [MediumPhone]. For another device, start from a preset and use
 * [copy]: `MediumPhone.copy(defaultDevice = "spec:width=393dp,height=851dp,dpi=440")` sizes
 * previews as the Pixel 4a that Roborazzi's Robolectric runtime defaults to.
 *
 * There is deliberately no default: the profile decides the size and density of every golden the
 * module records, and no value is right for every project, so the build asks rather than guessing.
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
   * The unit decides the rounding, the same way for this default and for a preview that names
   * its own device. A device in dp is converted once with `floor(dp * density)`, as Robolectric
   * does, so `"spec:width=411dp,height=914dp,dpi=420"` is 1078x2399px. A device in pixels - which
   * is what `"id:..."` usually resolves to - is rendered at exactly its own pixels, so
   * `"id:medium_phone"` is 1080x2400px.
   *
   * A preview that declares its own `device` is always sized by it, whatever this is set to.
   *
   * `null` keeps the historical desktop behaviour for previews without a `device`: density is
   * pinned at 1, the canvas is at least 1024x768, and only `widthDp`/`heightDp` affect the size.
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
     * The historical desktop behaviour for previews without a `device`: sized in raw pixels at
     * density 1, on a canvas of at least 1024x768.
     *
     * This is what the desktop runtime rendered for such previews before device profiles existed,
     * so it leaves their goldens unchanged. A preview that declares `@Preview(device = ...)` is
     * sized and scaled by that device, as under every other profile.
     */
    val Desktop: DesktopPreviewDeviceProfile = DesktopPreviewDeviceProfile(defaultDevice = null)

    /**
     * Sizes previews as the Medium Phone that Android Studio previews by default, so a preview can
     * be compared with what Studio, or Google's Compose Preview Screenshot Testing, shows.
     *
     * Written out as a spec rather than as `"id:medium_phone"` so the preset does not depend on
     * ComposablePreviewScanner's device table keeping that entry; these are the pixels and dpi the
     * scanner resolves `id:medium_phone` to, and they are the pixels rendered.
     *
     * Robolectric has the same device as `RobolectricDeviceQualifiers.MediumPhone`, but it carries
     * the configuration in dp, and at density 2.625 no whole dp reaches 1080 or 2400: 411dp is
     * 1078px and 412dp is 1081px. A Robolectric run of this device is therefore a pixel or two off
     * Studio however it is spelled, while this profile renders exactly what Studio does.
     *
     * What matches Studio here is the device configuration, not the text: see the class
     * documentation.
     */
    val MediumPhone: DesktopPreviewDeviceProfile =
      DesktopPreviewDeviceProfile(defaultDevice = "spec:width=1080px,height=2400px,dpi=420")

    /**
     * Spelled out for the message a build sees when it has configured no profile at all.
     *
     * Kept next to the presets so that adding one and forgetting to offer it here is a change to
     * this file rather than to a string somewhere in the Gradle plugin.
     */
    @InternalRoborazziApi
    val PRESET_CHOICES: String = """
      |  DesktopPreviewDeviceProfile.Desktop
      |      Density 1, so 1dp is 1px, on a surface of at least 1024x768, for previews
      |      without @Preview(device = ...). This is what the desktop runtime rendered for
      |      them before device profiles existed, so it leaves their goldens unchanged.
      |
      |  DesktopPreviewDeviceProfile.MediumPhone
      |      1080x2400px at 420dpi, the device Android Studio previews by default, so the
      |      same preview can be compared with Studio and with Google's Compose Preview
      |      Screenshot Testing.
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
