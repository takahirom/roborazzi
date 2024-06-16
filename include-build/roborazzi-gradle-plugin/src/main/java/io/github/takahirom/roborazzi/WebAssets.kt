package io.github.takahirom.roborazzi

import org.webjars.WebJarVersionLocator
import java.io.File

class WebAssets private constructor(private val webJarVersionLocator: WebJarVersionLocator) {
  private val materializeCss = "materializecss"
  private val materialIcons = "material-design-icons"
  private val webJarResource = "resources"

    fun writeWebAssets(reportDir: File) {
      writeLocalAssetsToRoborazziReportsDir(reportDir)
      writeWebJarAssetsToRoborazziReportsDir(reportDir)
    }

  private fun writeLocalAssetsToRoborazziReportsDir(reportDir: File) {
    writeAssets(
      assetName = "report-style.css",
      exactPath = "assets/report-style.css",
      reportDir = reportDir
    )
  }

  private fun writeWebJarAssetsToRoborazziReportsDir(reportDir: File) {
    getMaterializeMinCssPath()?.let { materializeMinCss ->
      writeAssets(
        assetName = "materialize.min.css",
        exactPath = "$webJarResource/$materializeMinCss",
        reportDir = reportDir
      )
    }
    getMaterializeMinJsPath()?.let { materializeMinJs ->
      writeAssets(
        assetName = "materialize.min.js",
        exactPath = "$webJarResource/$materializeMinJs",
        reportDir = reportDir
      )
    }
  }

  private fun outputFile(directory: File, filename: String): File {
    return File(directory, filename).apply {
      parentFile.apply { if (!exists()) mkdirs() }
    }
  }

  private fun getMaterializeMinCssPath(): String? {
    return webJarVersionLocator.locate(materializeCss, "css/materialize.min.css")
  }

  private fun getMaterializeMinJsPath(): String? {
    return webJarVersionLocator.locate(materializeCss, "js/materialize.min.js")
  }

  private fun getMaterialIconsCssPath(): String? {
    return webJarVersionLocator.locate(materializeCss, "css/material-icons.css")
  }

  private fun getMaterialIconsFontPath(): String? {
    return webJarVersionLocator.locate(materializeCss, "css/material-icons.woff2")
  }

  private fun WebJarVersionLocator.locate(webJarName: String, exactPath: String) = run {
    path(webJarName, exactPath)?.let { "webjars/$it" }
  }

  private fun writeAssets(
    assetName: String,
    exactPath: String,
    reportDir: File
  ) {
    val assetsDirectory = File(reportDir, "assets")
    val asset = this::class.java
      .classLoader
      .getResource("META-INF/$exactPath")?.readBytes()
    if (asset != null) {
      val assetFile = outputFile(assetsDirectory, assetName)
      assetFile.writeBytes(asset)
    }
  }

  companion object {
    fun create(webJarVersionLocator: WebJarVersionLocator = WebJarVersionLocator()): WebAssets {
      return WebAssets(webJarVersionLocator)
    }
  }
}