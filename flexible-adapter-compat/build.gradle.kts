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
    sourceSets.getByName("main") {
        java.srcDir("../vendor/FlexibleAdapter/flexible-adapter/src/main/java")
        res.srcDir("../vendor/FlexibleAdapter/flexible-adapter/src/main/res")
    }
}

dependencies {
    api(androidx.recyclerview)
}
