package com.jaredsburrows.license

import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.codehaus.plexus.util.ReaderFactory
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.reporting.ReportingExtension
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import java.io.File

/** Returns true if plugin exists in project. */
internal fun Project.hasPlugin(list: List<String>): Boolean = list.any { plugins.hasPlugin(it) }

private fun Project.includeParentPomFilesRecursively(
  mavenReader: MavenXpp3Reader,
  fileCollection: ConfigurableFileCollection,
  coordinateToFile: MutableMap<String, String>,
) {
  val pomFilesToInspect = ArrayDeque<File>()
  fileCollection.files.forEach { pomFilesToInspect.addLast(it) }

  val visitedPomFiles = hashSetOf<File>()
  val visitedParentCoordinates = hashSetOf<String>()

  while (pomFilesToInspect.isNotEmpty()) {
    val pomFile = pomFilesToInspect.removeFirst()
    if (!visitedPomFiles.add(pomFile)) {
      continue
    }

    val model =
      try {
        mavenReader.read(ReaderFactory.newXmlReader(pomFile), false)
      } catch (_: Exception) {
        continue
      }

    val parent = model.parent ?: continue
    val parentGroupId = parent.groupId.orEmpty().trim()
    val parentArtifactId = parent.artifactId.orEmpty().trim()
    val parentVersion = parent.version.orEmpty().trim()

    if (parentGroupId.isEmpty() || parentArtifactId.isEmpty() || parentVersion.isEmpty()) {
      continue
    }

    val parentCoordinate = "$parentGroupId:$parentArtifactId:$parentVersion"
    if (!visitedParentCoordinates.add(parentCoordinate)) {
      continue
    }
    if (coordinateToFile.containsKey(parentCoordinate)) {
      continue
    }

    val parentPomFile = resolvePomFile(parentGroupId, parentArtifactId, parentVersion) ?: continue
    coordinateToFile[parentCoordinate] = parentPomFile.absolutePath
    fileCollection.from(parentPomFile)
    pomFilesToInspect.addLast(parentPomFile)
  }
}

private fun Project.resolvePomFile(
  groupId: String,
  artifactId: String,
  version: String,
): File? {
  val result =
    dependencies
      .createArtifactResolutionQuery()
      .forModule(groupId, artifactId, version)
      .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
      .execute()

  val resolvedPomFiles =
    buildList {
      for (component in result.resolvedComponents) {
        for (artifact in component.getArtifacts(MavenPomArtifact::class.java)) {
          if (artifact is ResolvedArtifactResult) {
            add(artifact.file)
          }
        }
      }
    }.distinct()

  return resolvedPomFiles.firstOrNull()
}

/** Configure common configuration for both Java and Android tasks. */
internal fun Project.configureCommon(
  task: LicenseReportTask,
  configurationNames: List<String>,
) {
  val reportingExtension = extensions.getByType(ReportingExtension::class.java)
  val licenseExtension = extensions.getByType(LicenseReportExtension::class.java)

  val pomInput = buildPomInput(configurationNames, licenseExtension.explicitDependencies)

  task.apply {
    outputDir = reportingExtension.file("licenses")

    pomFiles.from(pomInput.files.files)
    pomFiles.disallowChanges()
    rootCoordinates = pomInput.rootCoordinates
    pomCoordinatesToFile = pomInput.coordinateToFile

    generateCsvReport = licenseExtension.generateCsvReport
    generateHtmlReport = licenseExtension.generateHtmlReport
    generateJsonReport = licenseExtension.generateJsonReport
    generateTextReport = licenseExtension.generateTextReport
    copyCsvReportToAssets = licenseExtension.copyCsvReportToAssets
    copyHtmlReportToAssets = licenseExtension.copyHtmlReportToAssets
    copyJsonReportToAssets = licenseExtension.copyJsonReportToAssets
    copyTextReportToAssets = licenseExtension.copyTextReportToAssets
    useVariantSpecificAssetDirs = licenseExtension.useVariantSpecificAssetDirs
    ignoredPatterns = licenseExtension.ignoredPatterns
    showVersions = licenseExtension.showVersions
    explicitDependencies = licenseExtension.explicitDependencies
  }
}

private data class PomInput(
  val files: ConfigurableFileCollection,
  val rootCoordinates: List<String>,
  val coordinateToFile: Map<String, String>,
)

private fun Project.buildPomInput(
  configurationNames: List<String>,
  explicitDependencies: List<String>?,
): PomInput {
  val fileCollection = objects.fileCollection()
  val coordinateToFile = sortedMapOf<String, String>()
  val roots = linkedSetOf<String>()

  val componentIdentifiers = linkedSetOf<ComponentIdentifier>()

  configurationNames
    .mapNotNull { configurations.findByName(it) }
    .filter { it.isCanBeResolved }
    .forEach { configuration ->
      configuration
        .incoming
        .resolutionResult
        .allComponents
        .map { it.id }
        .filterIsInstance<ModuleComponentIdentifier>()
        .forEach { componentIdentifiers += it }
    }

  if (componentIdentifiers.isNotEmpty()) {
    val pomArtifactResult =
      dependencies
        .createArtifactResolutionQuery()
        .forComponents(componentIdentifiers)
        .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
        .execute()

    pomArtifactResult.resolvedComponents.forEach { component ->
      val componentId = component.id
      if (componentId !is ModuleComponentIdentifier) {
        return@forEach
      }

      val pomFile =
        component
          .getArtifacts(MavenPomArtifact::class.java)
          .filterIsInstance<ResolvedArtifactResult>()
          .firstOrNull()
          ?.file
          ?: return@forEach

      val coordinate = "${componentId.group}:${componentId.module}:${componentId.version}"
      coordinateToFile.putIfAbsent(coordinate, pomFile.absolutePath)
      roots += coordinate
      fileCollection.from(pomFile)
    }
  }

  // When explicitDependencies is set, filter roots to the allowlist and perform
  // broad scan + fallback POM resolution for any missing entries.
  if (explicitDependencies != null) {
    val validDeps =
      explicitDependencies.filter { dep ->
        val parts = dep.split(":")
        val valid = parts.size >= 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()
        if (!valid) {
          logger.warn("explicitDependencies: ignoring malformed entry '$dep' (expected 'group:module' or 'group:module:version')")
        }
        valid
      }

    val allowlist =
      validDeps
        .map { dep ->
          val parts = dep.split(":")
          "${parts[0]}:${parts[1]}"
        }.toSet()

    val explicitVersionMap =
      validDeps.associate { dep ->
        val parts = dep.split(":")
        val key = "${parts[0]}:${parts[1]}"
        key to (if (parts.size >= 3) parts[2] else "")
      }

    // Filter roots to only those matching the allowlist
    val filteredRoots =
      roots
        .filter { coordinate ->
          val parts = coordinate.split(":")
          if (parts.size >= 2) "${parts[0]}:${parts[1]}" in allowlist else false
        }.toMutableList()

    // Identify which allowlisted dependencies are missing from the primary scan
    val foundKeys =
      filteredRoots
        .map { coordinate ->
          val parts = coordinate.split(":")
          "${parts[0]}:${parts[1]}"
        }.toSet()

    val missingKeys = allowlist - foundKeys

    if (missingKeys.isNotEmpty()) {
      logger.lifecycle(
        "explicitDependencies: ${missingKeys.size} dependencies not found in primary scan, performing broad scan...",
      )

      // Broad scan: iterate all resolvable configurations across rootProject + subprojects
      val broadComponents = linkedSetOf<ModuleComponentIdentifier>()
      val allProjects = rootProject.allprojects
      for (proj in allProjects) {
        for (config in proj.configurations.toList()) {
          if (!config.isCanBeResolved) continue
          try {
            config.incoming.resolutionResult.allComponents
              .map { it.id }
              .filterIsInstance<ModuleComponentIdentifier>()
              .filter { "${it.group}:${it.module}" in missingKeys }
              .forEach { broadComponents += it }
          } catch (_: Exception) {
            // Some configurations may fail to resolve; skip them
          }
        }
      }

      if (broadComponents.isNotEmpty()) {
        logger.lifecycle(
          "explicitDependencies: found ${broadComponents.size} components in broad scan, resolving POMs...",
        )

        // Batch POM resolution for broad-scanned components
        try {
          val broadResult =
            dependencies
              .createArtifactResolutionQuery()
              .forComponents(broadComponents.toList())
              .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
              .execute()

          broadResult.resolvedComponents.forEach { component ->
            val componentId = component.id
            if (componentId !is ModuleComponentIdentifier) return@forEach

            val pomFile =
              component
                .getArtifacts(MavenPomArtifact::class.java)
                .filterIsInstance<ResolvedArtifactResult>()
                .firstOrNull()
                ?.file
                ?: return@forEach

            val coordinate = "${componentId.group}:${componentId.module}:${componentId.version}"
            coordinateToFile.putIfAbsent(coordinate, pomFile.absolutePath)
            filteredRoots += coordinate
            fileCollection.from(pomFile)
          }
        } catch (e: Exception) {
          logger.warn("explicitDependencies: batch POM resolution failed: ${e.message}")
        }
      }

      // Per-module fallback for still-unresolved components
      val stillResolvedKeys =
        filteredRoots
          .map { coordinate ->
            val parts = coordinate.split(":")
            "${parts[0]}:${parts[1]}"
          }.toSet()

      val stillMissing = missingKeys - stillResolvedKeys

      for (key in stillMissing) {
        val (group, module) = key.split(":")
        val component = broadComponents.find { "${it.group}:${it.module}" == key }
        val version = component?.version ?: explicitVersionMap[key] ?: ""

        if (version.isNotEmpty()) {
          val pomFile = resolvePomFile(group, module, version)
          if (pomFile != null) {
            val coordinate = "$group:$module:$version"
            coordinateToFile.putIfAbsent(coordinate, pomFile.absolutePath)
            filteredRoots += coordinate
            fileCollection.from(pomFile)
          } else {
            logger.warn("explicitDependencies: could not resolve POM for $group:$module:$version")
          }
        } else {
          logger.warn("explicitDependencies: no version available for $group:$module, skipping")
        }
      }
    }

    // Replace roots with the filtered+augmented set
    roots.clear()
    roots.addAll(filteredRoots)
  }

  val mavenReader = MavenXpp3Reader()

  includeParentPomFilesRecursively(
    mavenReader = mavenReader,
    fileCollection = fileCollection,
    coordinateToFile = coordinateToFile,
  )

  return PomInput(
    files = fileCollection,
    rootCoordinates = roots.toList().sorted(),
    coordinateToFile = coordinateToFile,
  )
}
