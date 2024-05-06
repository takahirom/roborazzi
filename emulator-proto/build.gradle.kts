@file:Suppress("UnstableApiUsage")

plugins {
  kotlin("jvm")
  id("com.squareup.wire") version "4.9.9"
}

group = "com.github.takahirom.roborazzi.emulator.proto"
version = "1.0-SNAPSHOT"

dependencies {
  api(libs.grpc.protobuf)
  api(libs.wire.grpc.client)
}

wire {
  protoLibrary = true

//  kotlin {
//    rpcCallStyle = "suspending"
//    rpcRole = "client"
//    singleMethodServices = false
//  }
}
