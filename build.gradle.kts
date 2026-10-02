buildscript {
    dependencies {
        classpath(sylibs.gradleversionsx)
    }
}

plugins {
    alias(kotlinx.plugins.serialization) apply false
    alias(libs.plugins.aboutLibraries) apply false
    alias(libs.plugins.moko) apply false
    alias(libs.plugins.sqldelight) apply false
}

subprojects {
    configurations.configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("com.github.arkon.FlexibleAdapter:flexible-adapter"))
                .using(project(":flexible-adapter-compat"))
                .because("Build pinned FlexibleAdapter source when JitPack artifact is unavailable")
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
