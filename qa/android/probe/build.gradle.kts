import com.android.build.api.dsl.ApplicationExtension

val generated = layout.buildDirectory.dir("generated/production")
val production = rootProject.file("../../app/src/main/java/eu/kanade/tachiyomi/ui/dictionary")
val prepareProduction by tasks.registering {
    inputs.files(File(production, "ScrollLookupPolicy.kt"), File(production, "ScrollTranslateLookupAccessibilityService.kt"))
    outputs.dir(generated)
    doLast {
        val destination = generated.get().asFile.apply { mkdirs() }
        listOf("ScrollLookupPolicy.kt", "ScrollTranslateLookupAccessibilityService.kt").forEach { name ->
            val source = File(production, name).readText()
            File(destination, name).writeText(source.replace("com.google.android.googlequicksearchbox", "org.chimahon.qa.translator"))
        }
    }
}
extensions.configure<ApplicationExtension> {
    sourceSets.getByName("main").java.srcDir(generated)
}
tasks.named("preBuild").configure { dependsOn(prepareProduction) }
