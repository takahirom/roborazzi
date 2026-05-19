// Top-level build file where you can add configuration options common to all sub-projects/modules.
@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
  id("com.android.application") version libs.versions.agp apply false
  id("com.android.library") version libs.versions.agp apply false
  id("com.android.kotlin.multiplatform.library") version libs.versions.agp apply false
  id("org.jetbrains.kotlin.android") version libs.versions.kotlin apply false
  id("org.jetbrains.kotlin.multiplatform") version libs.versions.kotlin apply false
  id("org.jetbrains.compose") version libs.versions.composeMultiplatform apply false
  id("org.jetbrains.kotlin.plugin.compose") version libs.versions.kotlinComposeCompiler apply false
  // Just for Gradle Build, included build will be applied
  id("io.github.takahirom.roborazzi") version "1.0.0" apply false
}
// Apply the same Java compatibility for each module
val javaVersion = libs.versions.javaTarget.get()
val toolchainVersion = libs.versions.javaToolchain.get()

allprojects {
  val javaTargetVersion = JavaVersion.toVersion (javaVersion)
  val jvmTargetVersion = org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(javaVersion)
  
  // Replace AGP's default Compose Compiler with the embeddable compiler shipped
  // with Kotlin, and force it to match the main Kotlin compiler version. The
  // Compose Compiler Gradle Plugin is pinned to an older minor (see
  // kotlinComposeCompiler in libs.versions.toml) and would otherwise drag in
  // an embeddable compiler that mismatches the main compiler.
  configurations.all {
    resolutionStrategy.dependencySubstitution {
      substitute(module("androidx.compose.compiler:compiler"))
        .using(module("org.jetbrains.kotlin:kotlin-compose-compiler-plugin-embeddable:${libs.versions.kotlin.get()}"))
        .because("Compose Compiler ships with the Kotlin distribution since 2.0")
    }
    resolutionStrategy.eachDependency {
      if (requested.group == "org.jetbrains.kotlin"
        && requested.name == "kotlin-compose-compiler-plugin-embeddable") {
        useVersion(libs.versions.kotlin.get())
        because("Align embeddable compose compiler with main Kotlin compiler")
      }
    }
  }


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

  // Gradle 9 fails test tasks that have test sources but no discovered tests.
  // sample-generate-preview-tests exercises this path in scenarios that
  // deliberately produce zero discovered tests (e.g. the missing
  // roborazzi-compose-preview-scanner-support dependency assertion). Scope
  // the relax to the single empty task so other Test tasks still fail loudly.
  if (name == "sample-generate-preview-tests") {
    tasks.withType(org.gradle.api.tasks.testing.Test::class.java)
      .matching { it.name == "testDebugUnitTest" }
      .configureEach { failOnNoDiscoveredTests = false }
  }
}
true // Needed to make the Suppress annotation work for the plugins block