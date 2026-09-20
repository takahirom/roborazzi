package com.github.takahirom.roborazzi

import java.io.Serializable

/**
 * How the Compose Desktop preview runtime sizes and scales a preview.
 *
 * A profile is a per-test-run decision, not a per-preview one: one module can capture the same
 * previews under several profiles by giving each its own Kotlin test run, which the Roborazzi
 * plugin turns into its own set of tasks and its own output directory.
 *
 * The presets are [Desktop] and [AndroidCompatible]. To vary a single axis, start from a preset
 * and use [copy].
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
     */
    val Desktop: DesktopPreviewDeviceProfile = DesktopPreviewDeviceProfile(defaultDevice = null)

    /**
     * Sizes previews the way the Robolectric runtime does, so the two runtimes can be compared
     * preview by preview. Previews that name no device are sized as a Pixel 4a, which is also
     * Robolectric's default.
     */
    val AndroidCompatible: DesktopPreviewDeviceProfile =
      DesktopPreviewDeviceProfile(defaultDevice = "id:pixel_4a")

    /**
     * Used when no profile is configured.
     *
     * Deliberately an alias rather than a stored value: which preset it points at is a release
     * decision, and keeping it in one place means changing it does not touch any other code.
     */
    val Default: DesktopPreviewDeviceProfile get() = Desktop

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
