package io.github.takahirom.roborazzi

import org.webjars.WebJarVersionLocator
import java.io.File

class WebAssets private constructor(private val webJarVersionLocator: WebJarVersionLocator) {
  private val webJarResource = "resources"

  val assets = assetPathsMap.entries.flatMap {
    assets -> assets.value.map { it.substringAfterLast("/") }
  }

  fun writeToRoborazziReportsDir(reportDir: File) {
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
    assetPathsMap.forEach { (key, value) ->
      value.forEach { exactPath ->
        webJarVersionLocator.locate(key, exactPath)?.let {
          writeAssets(
            assetName = exactPath.substringAfterLast("/"),
            exactPath = "$webJarResource/$it",
            reportDir = reportDir
          )
        }
      }
    }
  }

  private fun outputFile(directory: File, filename: String): File {
    return File(directory, filename).apply {
      parentFile.apply { if (!exists()) mkdirs() }
    }
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

   private val assetPathsMap = mapOf(
      "materializecss" to listOf(
        "css/materialize.min.css",
        "js/materialize.min.js",
      ),
      "material-design-icons" to listOf(
        "material-icons.css",
        "MaterialIcons-Regular.ttf",
      )
    )

    fun create(
      webJarVersionLocator: WebJarVersionLocator = WebJarVersionLocator()
    ): WebAssets = WebAssets(webJarVersionLocator)
  }
}