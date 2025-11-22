package com.github.takahirom.roborazzi

fun getReportFileName(
  absolutePath: String?,
  variantName: String,
  timestampNs: Long,
  nameWithoutExtension: String
) = "$absolutePath/$variantName/${timestampNs}_$nameWithoutExtension.json"