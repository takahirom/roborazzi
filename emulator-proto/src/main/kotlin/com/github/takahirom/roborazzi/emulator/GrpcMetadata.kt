package com.github.takahirom.roborazzi.emulator

import io.grpc.Context
import io.grpc.Metadata
import io.grpc.Metadata.ASCII_STRING_MARSHALLER

object GrpcMetadata {
  val instanceMetadataKey = Metadata.Key.of("instance", ASCII_STRING_MARSHALLER)
  val instanceContextKey = Context.key<String>("instance")
}