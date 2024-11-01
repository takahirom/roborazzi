package com.github.takahirom.roborazzi

interface ImageIoFormat

@Suppress("FunctionName")
expect fun WebPImageIoFormat() : ImageIoFormat

expect fun ImageIoFormat() : ImageIoFormat