plugins {
    id("mihon.library")
}

// Build the exact upstream revision requested by Chimahon, without relying on
// the unavailable JitPack binary. The pinned submodule retains its own license.
android {
    namespace = "eu.davidea.flexibleadapter"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        // Legacy source logs this field; modern Android library plugins omit it.
        buildConfigField("String", "VERSION_NAME", "\"c8013533\"")
    }
    sourceSets.getByName("main") {
        java.srcDir("../vendor/FlexibleAdapter/flexible-adapter/src/main/java")
        res.srcDir("../vendor/FlexibleAdapter/flexible-adapter/src/main/res")
    }
}

dependencies {
    api(androidx.recyclerview)
}
