package com.github.takahirom.roborazzi.emulator

import com.github.takahirom.roborazzi.emulator.GrpcMetadata.instanceContextKey
import com.github.takahirom.roborazzi.emulator.GrpcMetadata.instanceMetadataKey
import io.grpc.Context
import io.grpc.Contexts
import io.grpc.Metadata
import io.grpc.ServerBuilder
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.ServerRegistry
import io.grpc.netty.shaded.io.grpc.netty.NettyServerProvider
import kotlinx.coroutines.Dispatchers
import okio.Closeable

class GrpcServer : Closeable {
  val emulatorControllerService = EmulatorControllerService(Dispatchers.Main)
  val roborazziService = RoborazziService(Dispatchers.Main)

  val server by lazy {
    ServerBuilder.forPort(8080)
      .addService(emulatorControllerService)
      .addService(roborazziService)
      .addService(RtcService())
      .intercept(object : ServerInterceptor {
        override fun <ReqT : Any?, RespT : Any?> interceptCall(
          call: ServerCall<ReqT, RespT>,
          headers: Metadata,
          next: ServerCallHandler<ReqT, RespT>
        ): ServerCall.Listener<ReqT> {
          println("Call: ${call.methodDescriptor.bareMethodName}")

          val instance = headers[instanceMetadataKey]

          val newContext = Context.current().withValue(instanceContextKey, instance)

          return Contexts.interceptCall(newContext, call, headers, next)
        }
      })
      .build()
  }

  override fun close() {
    server.shutdownNow()
  }

  fun start() {
    server.start()
  }

  companion object {
    init {
      ServerRegistry.getDefaultRegistry().register(NettyServerProvider())
    }
  }
}