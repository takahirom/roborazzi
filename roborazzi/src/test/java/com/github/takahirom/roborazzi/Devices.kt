@file:Suppress("PLUGIN_IS_NOT_ENABLED")

package com.github.takahirom.roborazzi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import kotlin.math.roundToInt

@Serializable
@XmlSerialName("devices", namespace = "http://schemas.android.com/sdk/devices/1", prefix = "d")
data class Devices(val devices: List<Device>)

@Serializable
@XmlSerialName("device", namespace = "http://schemas.android.com/sdk/devices/1", prefix = "d")
data class Device(
  @XmlElement(value = true) val name: String,
  val hardware: Hardware,
  @XmlElement(value = true) @SerialName("tag-id") val tagId: String?,
  @XmlElement(value = true) val bootProps: BootProps?,
) {
  val qualifier: String
    get() = listOf(
      "w${hardware.screen.widthDp}dp",
      "h${hardware.screen.heightDp}dp",
      hardware.screen.screenSize,
      screenRatio,
      shape,
      type,
      hardware.screen.pixelDensity,
      "keyshidden",
      hardware.nav
    ).joinToString("-")

  val screenRatio: String
    get() = if (hardware.screen.screenRatio == "long") "long" else "notlong"

  val shape: String
    get() = if (bootProps?.bootProps?.find { it.propName == "ro.emulator.circular" }?.propValue == "true") "round"
    else "notround"

  val type: String
    get() = when (tagId) {
      "android-wear" -> {
        "watch"
      }

      "android-tv" -> {
        "television"
      }

      "android-automotive-playstore" -> {
        "car"
      }

      else -> {
        "any"
      }
    }
}

@Serializable
@XmlSerialName("hardware", namespace = "http://schemas.android.com/sdk/devices/1", prefix = "d")
data class Hardware(
  val screen: Screen,
  @XmlElement(value = true) val nav: String,
)

@Serializable
@XmlSerialName("screen", namespace = "http://schemas.android.com/sdk/devices/1", prefix = "d")
data class Screen(
  @XmlElement(value = true) @SerialName("screen-size") val screenSize: String,
  @XmlElement(value = true) @SerialName("screen-ratio") val screenRatio: String,
  @XmlElement(value = true) @SerialName("pixel-density") val pixelDensity: String,
  @XmlElement(value = true) val dimensions: Dimensions
) {
  val density: Double
    get() = when (pixelDensity) {
      "ldpi" -> 120
      "mdpi" -> 160
      "hdpi" -> 240
      "xhdpi" -> 320
      "xxhdpi" -> 480
      "xxxhdpi" -> 640
      "tvdpi" -> 213
      else -> pixelDensity.dropLast(3).toInt()
    }.toDouble()

  val widthDp: Int
    get() = (dimensions.xDimension / (density / 160)).roundToInt()
  val heightDp: Int
    get() = (dimensions.yDimension / (density / 160)).roundToInt()
}

@Serializable
@XmlSerialName("dimensions", namespace = "http://schemas.android.com/sdk/devices/1", prefix = "d")
data class Dimensions(
  @XmlElement(value = true) @SerialName("x-dimension") val xDimension: Int,
  @XmlElement(value = true) @SerialName("y-dimension") val yDimension: Int
)

@Serializable
@XmlSerialName("boot-props", namespace = "http://schemas.android.com/sdk/devices/1", prefix = "d")
data class BootProps(
  val bootProps: List<BootProp>
)

@Serializable
@XmlSerialName("boot-prop", namespace = "http://schemas.android.com/sdk/devices/1", prefix = "d")
data class BootProp(
  @XmlElement(value = true) @SerialName("prop-name") val propName: String,
  @XmlElement(value = true) @SerialName("prop-value") val propValue: String
)