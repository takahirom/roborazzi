@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  id("com.squareup.wire") version "4.9.9"
}

group = "com.github.takahirom.roborazzi.emulator.client"
version = "1.0-SNAPSHOT"

dependencies {
  api(libs.grpc.protobuf)
  implementation(libs.grpc.kotlin.stub)
  implementation(libs.wire.grpc.client)

  implementation(project(":emulator-proto"))
  protoPath(project(":emulator-proto"))
}

wire {
  sourcePath {
    srcProject(":emulator-proto")
  }

  kotlin {
    rpcRole = "client"
    singleMethodServices = false
    rpcCallStyle = "suspending"
  }
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    freeCompilerArgs += "-Xcontext-receivers"
  }
}