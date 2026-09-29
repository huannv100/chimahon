buildscript {
    dependencies {
        classpath(sylibs.gradleversionsx)
    }
}

plugins {
    alias(kotlinx.plugins.serialization) apply false
    alias(libs.plugins.aboutLibraries) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.moko) apply false
    alias(libs.plugins.sqldelight) apply false
}

subprojects {
    configurations.configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("com.github.arkon.FlexibleAdapter:flexible-adapter"))
                .using(project(":flexible-adapter-compat"))
                .because("Build the same pinned c8013533 source when its JitPack artifact is unavailable")
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
