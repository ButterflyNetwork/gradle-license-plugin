package com.jaredsburrows.license

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import org.gradle.api.Project
import java.util.Locale

/** Returns true if Android Gradle project. */
internal fun Project.isAndroidProject(): Boolean =
  hasPlugin(
    listOf(
      "android",
      "com.android.application",
      "android-library",
      "com.android.library",
      "com.android.test",
    ),
  )

/** Configure for Android projects. */
internal fun Project.configureAndroidProject() {
  // Match by plugin id, not AGP class, so this is safe to call on non-Android projects.
  plugins.withId("com.android.application") { registerVariantTasks() }
  plugins.withId("com.android.library") { registerVariantTasks() }
  plugins.withId("com.android.test") { registerVariantTasks() }
}

private fun Project.registerVariantTasks() {
  val androidComponents =
    extensions.findByType(AndroidComponentsExtension::class.java) ?: return
  androidComponents.onVariants { variant ->
    configureVariant(variant)
  }
}

private fun Project.configureVariant(variant: Variant) {
  val name =
    variant.name.replaceFirstChar {
      if (it.isLowerCase()) {
        it.titlecase(Locale.getDefault())
      } else {
        it.toString()
      }
    }

  tasks.register("license${name}Report", LicenseReportTask::class.java) {
    // Apply common task configuration first
    configureCommon(
      it,
      listOf(
        "${variant.name}CompileClasspath",
        "${variant.name}RuntimeClasspath",
      ),
    )

    // Custom for Android tasks
    val sourceSetName = if (it.useVariantSpecificAssetDirs) variant.name else "main"

    @Suppress("UnstableApiUsage")
    val commonExtension = extensions.getByType(CommonExtension::class.java)
    val assetDirectoryPaths =
      commonExtension.sourceSets
        .findByName(sourceSetName)
        ?.assets
        ?.directories
        .orEmpty()
    it.assetDirs = assetDirectoryPaths.map { path -> file(path) }
    it.variantName = variant.name
  }
}
