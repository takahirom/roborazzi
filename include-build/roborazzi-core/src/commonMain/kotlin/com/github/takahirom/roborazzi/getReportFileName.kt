package com.github.takahirom.roborazzi

fun getReportFileName(
  absolutePath: String?,
  timestampNs: Long,
  nameWithoutExtension: String
) = "$absolutePath/${timestampNs}_$nameWithoutExtension.json"