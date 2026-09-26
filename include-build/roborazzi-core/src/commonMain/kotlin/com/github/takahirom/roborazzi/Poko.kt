package com.github.takahirom.roborazzi

/**
 * Generates value-based `equals`, `hashCode` and `toString` for the class from its primary
 * constructor properties, without the `copy` and `componentN` a data class would add to the API.
 *
 * This is the marker the Poko compiler plugin is configured with, so Roborazzi does not publish a
 * dependency on poko-annotations.
 */
@InternalRoborazziApi
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class Poko
