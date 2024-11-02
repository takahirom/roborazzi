package com.github.takahirom.roborazzi

interface ImageIoFormat

@Suppress("FunctionName")
expect fun LosslessWebPImageIoFormat() : ImageIoFormat

expect fun ImageIoFormat() : ImageIoFormat