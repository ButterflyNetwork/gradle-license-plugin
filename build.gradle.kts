plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
}

dependencies {
    implementation(localGroovy())
    implementation("org.apache.groovy:groovy-xml:4.0.27")
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.2")
    implementation("com.google.code.gson:gson:2.8.6")
    compileOnly("com.android.tools.build:gradle:8.11.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

gradlePlugin {
    plugins {
        create("license") {
            id = "com.jaredsburrows.license"
            implementationClass = "com.jaredsburrows.license.LicensePlugin"
        }
    }
}
