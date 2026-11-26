package com.github.takahirom.roborazzi

/**
 * The Pixel 4a that Roborazzi's Robolectric runtime defaults to, built the way the documentation
 * tells users to build it. The size expectations in these tests were measured against that
 * runtime, so they are written for this device.
 */
@OptIn(ExperimentalRoborazziApi::class)
internal val Pixel4aDeviceProfile: DesktopPreviewDeviceProfile =
  DesktopPreviewDeviceProfile.MediumPhone.copy(defaultDevice = "spec:width=393dp,height=851dp,dpi=440")
