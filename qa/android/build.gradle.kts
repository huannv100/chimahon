import com.android.build.api.dsl.ApplicationExtension

plugins {
    id("com.android.application") version "8.13.2" apply false
    kotlin("android") version "2.4.0" apply false
}

subprojects {
    apply(plugin = "com.android.application")
    apply(plugin = "org.jetbrains.kotlin.android")
    extensions.configure<ApplicationExtension> {
        namespace = "org.chimahon.qa.${project.name}"
        compileSdk = 36
        defaultConfig {
            applicationId = namespace
            minSdk = 34
            targetSdk = 35
            versionCode = 1
            versionName = "qa-only"
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
    }
}
