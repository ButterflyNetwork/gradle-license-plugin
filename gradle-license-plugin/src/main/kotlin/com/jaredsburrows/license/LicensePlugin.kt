package com.jaredsburrows.license

import org.gradle.api.Plugin
import org.gradle.api.Project

/** A [Plugin] which grabs the POM.xml files from maven dependencies. */
class LicensePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.extensions.add("licenseReport", LicenseReportExtension::class.java)

    // Configure Android variant tasks at apply time. The AGP modern variant API
    // (`AndroidComponentsExtension.onVariants`) must be registered before project
    // evaluation completes, so this cannot be wrapped in `afterEvaluate`.
    project.configureAndroidProject()

    // Java configuration is safe to defer to afterEvaluate since it doesn't use
    // the AGP variant API.
    project.afterEvaluate {
      val isAndroid = project.isAndroidProject()
      val isJava = project.isJavaProject()
      when {
        isAndroid -> Unit // already configured at apply time
        isJava -> project.configureJavaProject()
        else -> throw UnsupportedOperationException(
          "'com.jaredsburrows.license' requires Java, Kotlin or Android Gradle based plugins.",
        )
      }
    }
  }
}
