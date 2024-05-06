package com.github.takahirom.roborazzi.emulator

import io.grpc.Metadata
import io.grpc.ServerBuilder
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.ServerRegistry
import io.grpc.netty.shaded.io.grpc.netty.NettyServerProvider
import kotlinx.coroutines.Dispatchers

suspend fun main() {
  val roboEnv = RoboEnv()

  ServerRegistry.getDefaultRegistry().register(NettyServerProvider())

  val emulatorControllerService = EmulatorControllerService(Dispatchers.Main)

  println(emulatorControllerService.getBattery(Unit))

  val server = ServerBuilder.forPort(8080)
    .addService(emulatorControllerService)
    .addService(RtcService())
    .intercept(object : ServerInterceptor {
      override fun <ReqT : Any?, RespT : Any?> interceptCall(
        call: ServerCall<ReqT, RespT>,
        headers: Metadata,
        next: ServerCallHandler<ReqT, RespT>
      ): ServerCall.Listener<ReqT> {
        println("Call: ${call.methodDescriptor.bareMethodName}")
        return next.startCall(call, headers)
      }
    })
    .build()

  server.start()
  server.awaitTermination()
}
