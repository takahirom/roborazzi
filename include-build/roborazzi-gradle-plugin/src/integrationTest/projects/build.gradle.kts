// Top-level build file where you can add configuration options common to all sub-projects/modules.
@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
  id("com.android.application") apply false
  id("org.jetbrains.kotlin.android") apply false
  id("org.jetbrains.kotlin.multiplatform") apply false
  // Just for Gradle Build, included build will be applied
  id("io.github.takahirom.roborazzi") version "1.13.0" apply false
}
true // Needed to make the Suppress annotation work for the plugins block