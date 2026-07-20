package com.github.takahirom.roborazzi

// iOS always requires an explicit golden file path (there is no test-name based
// naming strategy), so the directory-encoding strategies never apply here.
internal actual fun roborazziIsSubdirectoryNamingStrategy(): Boolean = false
