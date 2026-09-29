plugins {
    kotlin("jvm") version "2.4.0"
    application
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
    sourceSets.getByName("main").kotlin {
        srcDir("../../app/src/main/java/eu/kanade/tachiyomi/ui/dictionary")
        srcDir("../../app/src/test/kotlin/eu/kanade/tachiyomi/ui/dictionary")
        include("ScrollLookupPolicy.kt", "ScrollLookupPolicyChecks.kt", "RunChecks.kt")
    }
}

application {
    mainClass.set("eu.kanade.tachiyomi.ui.dictionary.RunChecksKt")
}
