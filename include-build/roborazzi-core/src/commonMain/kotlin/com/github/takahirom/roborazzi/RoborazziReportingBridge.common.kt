package com.github.takahirom.roborazzi

/**
 * Publishes a freshly written capture image (golden / actual / compare) to an
 * optional external reporting integration so it can be attached to test reports.
 *
 * On JVM/Android this forwards to [RoborazziReportingBridge]; when no reporting
 * engine wrapper is installed it is a no-op. Other targets (iOS, ...) have no
 * reporting bridge, so their actual implementation does nothing.
 */
internal expect fun roborazziReportCapturedImage(absolutePath: String)
