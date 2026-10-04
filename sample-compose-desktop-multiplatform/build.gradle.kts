import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("multiplatform")
  id("org.jetbrains.compose")
  id("org.jetbrains.kotlin.plugin.compose")
  id("io.github.takahirom.roborazzi")
}

group = "com.github.takahirom.roborazzi.compose.desktop.kmp.sample"
version = "1.0-SNAPSHOT"

// ComposablePreviewScanner publishes JVM 17 metadata/bytecode, so the desktop
// target of this sample targets 17 (the repository default is 11). This
// configureEach runs after the allprojects one in the root build file, so 17 wins.
tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
}
// The consumer attribute of the KMP test classpaths is not derived from the
// compiler options above, so raise it explicitly to resolve the JVM-17 artifacts.
afterEvaluate {
  listOf("desktopTestCompileClasspath", "desktopTestRuntimeClasspath").forEach { name ->
    configurations.named(name) {
      attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
    }
  }
}

kotlin {
  jvm("desktop") {
        // JVM 17 at the DSL level so the consumer attribute allows resolving the
        // JVM-17 ComposablePreviewScanner and preview-scanner-support artifacts.
        compilerOptions {
          jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
        // Test case for issue #754: Verify that multiple test runs can be created
        // for the same target without task name conflicts
        val customCompilation by compilations.creating {}
        testRuns.create("custom") {
            setExecutionSourceFrom(customCompilation)
        }
  }
  sourceSets {
    val desktopMain by getting {
      dependencies {
        // Note, if you develop a library, you should use compose.desktop.common.
        // compose.desktop.currentOs should be used in launcher-sourceSet
        // (in a separate module for demo project and in testMain).
        // With compose.desktop.common you will also lose @Preview functionality
        implementation(compose.desktop.currentOs)
        implementation(compose.components.uiToolingPreview)
      }
    }

    val desktopTest by getting {
      dependencies {
        implementation(project(":roborazzi-compose-desktop"))
        implementation(project(":roborazzi-compose-desktop-preview-scanner-support"))
        implementation(kotlin("test"))
        implementation(libs.composable.preview.scanner)
      }
    }
  }
}
compose.desktop {
  application {
    mainClass = "MainKt"

    nativeDistributions {
      targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
      packageName = "app-compose-desktop"
      packageVersion = "1.0.0"
    }
  }
}

