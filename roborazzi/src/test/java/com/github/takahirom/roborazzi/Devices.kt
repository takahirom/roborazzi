package com.github.takahirom.roborazzi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@XmlSerialName("devices", namespace = "http://schemas.android.com/sdk/devices/1", prefix = "d")
data class Devices(val devices: List<Device>)

@Serializable
@XmlSerialName("device", namespace = "http://schemas.android.com/sdk/devices/1", prefix = "d")
data class Device(
  @XmlElement(value = true)
  val name: String,
  val hardware: Hardware,
  @XmlElement(value = true)
  @SerialName("tag-id")
  val tagId: String?,
)

@Serializable
@XmlSerialName("hardware", namespace = "http://schemas.android.com/sdk/devices/1", prefix = "d")
data class Hardware(
  val screen: Screen,
  @XmlElement(value = true)
  val nav: String,
)

@Serializable
@XmlSerialName("screen", namespace = "http://schemas.android.com/sdk/devices/1", prefix = "d")
data class Screen(
  @XmlElement(value = true)
  @SerialName("screen-size")
  val screenSize: String,
  @XmlElement(value = true)
  @SerialName("screen-ratio")
  val screenRatio: String,
  @XmlElement(value = true)
  @SerialName("pixel-density")
  val pixelDensity: String,
  @XmlElement(value = true)
  val dimensions: Dimensions
)

@Serializable
@XmlSerialName("dimensions", namespace = "http://schemas.android.com/sdk/devices/1", prefix = "d")
data class Dimensions(
  @XmlElement(value = true)
  @SerialName("x-dimension")
  val xDimension: Int,
  @XmlElement(value = true)
  @SerialName("y-dimension")
  val yDimension: Int
)