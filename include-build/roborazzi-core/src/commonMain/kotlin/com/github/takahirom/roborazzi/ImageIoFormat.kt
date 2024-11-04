package com.github.takahirom.roborazzi

@ExperimentalRoborazziApi
interface ImageIoFormat

@ExperimentalRoborazziApi
@Suppress("FunctionName")
expect fun LosslessWebPImageIoFormat() : ImageIoFormat

@ExperimentalRoborazziApi
expect fun ImageIoFormat() : ImageIoFormat