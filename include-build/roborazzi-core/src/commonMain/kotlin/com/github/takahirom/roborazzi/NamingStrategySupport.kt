package com.github.takahirom.roborazzi

/**
 * Whether the active default naming strategy encodes the test package as
 * directories (the `*Dir` strategies). This is the signal that a golden's
 * subdirectories are package structure and must be mirrored when placing the
 * generated `_compare` / `_actual` images under a separate compare output
 * directory. Platforms without a test-name based naming strategy (e.g. iOS, which
 * always requires an explicit file path) return `false`.
 */
internal expect fun roborazziIsSubdirectoryNamingStrategy(): Boolean
