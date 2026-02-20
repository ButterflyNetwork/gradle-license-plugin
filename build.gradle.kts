plugins {
  `kotlin-dsl`
  id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
}

repositories {
  mavenCentral()
  google()
}

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.12.0")
  implementation("com.squareup.moshi:moshi:1.15.2")
  implementation("org.apache.maven:maven-model:3.9.12")
  compileOnly("com.android.tools.build:gradle:8.11.1")
}

gradlePlugin {
  plugins {
    create("licensePlugin") {
      id = "com.jaredsburrows.license"
      implementationClass = "com.jaredsburrows.license.LicensePlugin"
    }
  }
}
