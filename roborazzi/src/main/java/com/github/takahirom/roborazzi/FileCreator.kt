package com.github.takahirom.roborazzi

import java.io.File
import org.junit.runner.Description

typealias FileProvider = (Description, File, String) -> File
