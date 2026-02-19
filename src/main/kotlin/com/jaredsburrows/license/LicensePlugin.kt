package com.jaredsburrows.license

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import java.io.File

/** A [Plugin] which grabs the POM.xml files from maven dependencies. */
class LicensePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = project.extensions.create("licenseReport", LicenseReportExtension::class.java)

    project.plugins.withType(JavaPlugin::class.java) {
      configureJavaProject(project, extension)
    }
    project.plugins.withType(LibraryPlugin::class.java) {
      configureAndroidProject(project, extension)
    }
    project.plugins.withType(AppPlugin::class.java) {
      configureAndroidProject(project, extension)
    }
  }

  /** Configure for Java projects. */
  private fun configureJavaProject(project: Project, extension: LicenseReportExtension) {
    val taskName = "licenseReport"
    val path = "${project.layout.buildDirectory.get().asFile}/reports/licenses/$taskName".replace('/', File.separatorChar)

    // Create tasks
    project.tasks.create(taskName, LicenseReportTask::class.java).apply {
      description = "Outputs licenses report."
      group = "Reporting"
      csvFile = File(path + LicenseReportTask.CSV_EXT)
      htmlFile = File(path + LicenseReportTask.HTML_EXT)
      jsonFile = File(path + LicenseReportTask.JSON_EXT)
      generateCsvReport = extension.generateCsvReport
      generateHtmlReport = extension.generateHtmlReport
      generateJsonReport = extension.generateJsonReport
      explicitDependencies = extension.explicitDependencies
      copyCsvReportToAssets = false
      copyHtmlReportToAssets = false
      copyJsonReportToAssets = false
    }
  }

  /** Configure for Android projects. */
  private fun configureAndroidProject(
    project: Project,
    extension: LicenseReportExtension,
  ) {
    val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
    androidComponents.onVariants { variant ->
      val name = variant.name.replaceFirstChar { it.titlecase() }
      val taskName = "license${name}Report"
      val path = "${project.layout.buildDirectory.get().asFile}/reports/licenses/$taskName".replace('/', File.separatorChar)

      // Create tasks based on variant
      project.tasks.register(taskName, LicenseReportTask::class.java).configure {
        description = "Outputs licenses report for $name variant."
        group = "Reporting"
        csvFile = File(path + LicenseReportTask.CSV_EXT)
        htmlFile = File(path + LicenseReportTask.HTML_EXT)
        jsonFile = File(path + LicenseReportTask.JSON_EXT)
        generateCsvReport = extension.generateCsvReport
        generateHtmlReport = extension.generateHtmlReport
        generateJsonReport = extension.generateJsonReport
        copyCsvReportToAssets = extension.copyCsvReportToAssets
        copyHtmlReportToAssets = extension.copyHtmlReportToAssets
        copyJsonReportToAssets = extension.copyJsonReportToAssets
        explicitDependencies = extension.explicitDependencies
        @Suppress("DEPRECATION")
        assetDirs = (
          project
            .extensions
            .getByName("android") as BaseExtension
          )
          .sourceSets
          .getByName("main")
          .assets
          .srcDirs
          .toList()
        buildType = variant.buildType
        variantName = variant.name
      }
    }
  }
}
