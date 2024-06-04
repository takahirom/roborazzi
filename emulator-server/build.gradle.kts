@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("com.android.library")
  kotlin("android")
  id("org.jetbrains.compose")
  id("com.squareup.wire") version "4.9.9"
}

android {
  compileSdk = 34

  defaultConfig {
    minSdk = 21
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    buildConfig = false
  }

  kotlinOptions {
    jvmTarget = "11"
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }

  namespace = "com.github.takahirom.roborazzi.emulator.server"
}

buildscript {
  dependencies {
    classpath(libs.server.generator)
  }
}

group = "com.github.takahirom.roborazzi.emulator.server"
version = "1.0-SNAPSHOT"

dependencies {
  api(libs.grpc.protobuf)
  implementation(libs.server)
  implementation(libs.androidx.monitor)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.androidx.core)
  implementation(libs.grpc.kotlin.stub)
  implementation(libs.androidx.compose.runtime)
  implementation(libs.androidx.compose.material)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.ui.tooling)
  implementation(libs.androidx.activity)

  // for use with shadows etc
  implementation(libs.robolectric)

  protoPath(project(":emulator-proto"))
  implementation(project(":emulator-proto"))

  testImplementation(libs.grpc.netty.shaded)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}

wire {
  sourcePath {
    srcProject(":emulator-proto")
  }

  custom {
    schemaHandlerFactory = com.squareup.wire.kotlin.grpcserver.GrpcServerSchemaHandler.Factory()
    options = mapOf(
      "singleMethodServices" to "false",
      "rpcCallStyle" to "suspending",
    )
    exclusive = false
  }

  kotlin {
    rpcRole = "server"
    singleMethodServices = false
    rpcCallStyle = "suspending"
  }
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    freeCompilerArgs += "-Xcontext-receivers"
  }
}