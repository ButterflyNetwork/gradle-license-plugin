package com.jaredsburrows.license

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.TestExtension
import com.android.build.gradle.TestPlugin
import com.android.build.gradle.api.BaseVariant
import org.gradle.api.DomainObjectSet
import org.gradle.api.Project
import java.util.Locale

/** Returns true if Android Gradle project. */
internal fun Project.isAndroidProject(): Boolean =
  hasPlugin(
    listOf(
      // AppPlugin
      "android",
      "com.android.application",
      // LibraryPlugin
      "android-library",
      "com.android.library",
      // TestPlugin
      "com.android.test",
    ),
  )

/**
 * Configure for Android projects.
 *
 * AppPlugin - "android", "com.android.application"
 * LibraryPlugin - "android-library", "com.android.library"
 * TestPlugin - "com.android.test"
 */
internal fun Project.configureAndroidProject() {
  plugins.withType(AppPlugin::class.java) {
    extensions.getByType(AppExtension::class.java).run {
      configureVariant(this, applicationVariants)
      configureVariant(this, testVariants)
      configureVariant(this, unitTestVariants)
    }
  }

  plugins.withType(LibraryPlugin::class.java) {
    extensions.getByType(LibraryExtension::class.java).run {
      configureVariant(this, libraryVariants)
      configureVariant(this, testVariants)
      configureVariant(this, unitTestVariants)
    }
  }

  plugins.withType(TestPlugin::class.java) {
    extensions.getByType(TestExtension::class.java).run {
      configureVariant(this, applicationVariants)
    }
  }
}

private fun Project.configureVariant(
  baseExtension: BaseExtension,
  variants: DomainObjectSet<out BaseVariant>? = null,
) {
  // Configure tasks for all variants
  variants?.configureEach {
    val variant = this
    val variantDisplayName =
      variant.name.replaceFirstChar {
        if (it.isLowerCase()) {
          it.titlecase(Locale.getDefault())
        } else {
          it.toString()
        }
      }

    tasks.register("license${variantDisplayName}Report", LicenseReportTask::class.java) {
      // Apply common task configuration first
      configureCommon(
        this,
        listOf(
          "${variant.name}CompileClasspath",
          "${variant.name}RuntimeClasspath",
        ),
      )

      // Custom for Android tasks
      val sourceSetName = if (useVariantSpecificAssetDirs) variant.name else "main"
      assetDirs = baseExtension.sourceSets
        .findByName(sourceSetName)
        ?.assets
        ?.srcDirs
        ?.toList() ?: emptyList()
      variantName = variant.name
    }
  }
}
