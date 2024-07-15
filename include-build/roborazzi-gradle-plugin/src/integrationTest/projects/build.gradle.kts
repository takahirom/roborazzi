// Top-level build file where you can add configuration options common to all sub-projects/modules.
@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
  id("com.android.application") apply false
  id("org.jetbrains.kotlin.android") apply false
  id("org.jetbrains.kotlin.multiplatform") apply false
  // Just for Gradle Build, included build will be applied
  id("io.github.takahirom.roborazzi") version "1.0.0" apply false
}
// Apply the same Java compatibility for each module
val javaVersion = libs.versions.javaTarget.get()
val toolchainVersion = libs.versions.javaToolchain.get()

allprojects {
  val javaTargetVersion = JavaVersion.toVersion (javaVersion)
  val jvmTargetVersion = org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(javaVersion)

  plugins.withId("java") {
    configure<org.gradle.api.plugins.JavaPluginExtension> {
      toolchain {
        languageVersion.set(JavaLanguageVersion.of(toolchainVersion))
      }
    }
  }

  fun configureAndroid(extension: com.android.build.gradle.BaseExtension) {
    extension.compileOptions {
      sourceCompatibility = javaTargetVersion
      targetCompatibility = javaTargetVersion
    }
  }
  plugins.withType<com.android.build.gradle.BasePlugin>().configureEach {
    when (this) {
      is com.android.build.gradle.AppPlugin -> {
        extensions.configure<com.android.build.gradle.internal.dsl.BaseAppModuleExtension> {
          configureAndroid(this)
        }
      }
      is com.android.build.gradle.LibraryPlugin -> {
        extensions.configure<com.android.build.gradle.LibraryExtension> {
          configureAndroid(this)
        }
      }
    }
  }

  tasks.withType(org.gradle.api.tasks.compile.JavaCompile::class.java).configureEach {
    sourceCompatibility = javaTargetVersion.toString()
    targetCompatibility = javaTargetVersion.toString()
  }

  tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
    compilerOptions {
      jvmTarget.set(jvmTargetVersion)
    }
  }
}
true // Needed to make the Suppress annotation work for the plugins block