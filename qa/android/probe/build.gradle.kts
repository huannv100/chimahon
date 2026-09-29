import com.android.build.api.dsl.ApplicationExtension

val generated = layout.buildDirectory.dir("generated/production")
val production = rootProject.file("../../app/src/main/java/eu/kanade/tachiyomi/ui/dictionary")
val prepareProduction by tasks.registering {
    inputs.files(File(production, "ScrollLookupPolicy.kt"), File(production, "ScrollTranslateLookupAccessibilityService.kt"))
    outputs.dir(generated)
    doLast {
        val destination = generated.get().asFile.apply { mkdirs() }
        listOf("ScrollLookupPolicy.kt", "ScrollTranslateLookupAccessibilityService.kt").forEach { name ->
            var source = File(production, name).readText()
                .replace("com.google.android.googlequicksearchbox", "org.chimahon.qa.translator")
            if (name.endsWith("AccessibilityService.kt")) {
                // Diagnostics plus a test-only input producer. Production decisions stay unchanged.
                source = source.replace("        operational = true", "        operational = true\n        android.util.Log.i(\"ScrollProbe\", \"SERVICE_CONNECTED\")\n        ProbeGestureDriver.install(this)")
                    .replace("        override fun onMotionEvent(event: MotionEvent) {", "        override fun onMotionEvent(event: MotionEvent) {\n            android.util.Log.i(\"ScrollProbe\", \"MOTION \${event.actionMasked} routing=\$routing targets=\${targets.size}\")")
                    .replace("        override fun onStateChanged(state: Int) {", "        override fun onStateChanged(state: Int) {\n            android.util.Log.i(\"ScrollProbe\", \"STATE \$state pending=\${pendingTarget != null}\")")
                    .replace("            setRouting(wanted)", "            if (wanted != routing) android.util.Log.i(\"ScrollProbe\", \"ROUTING \$wanted google=\$googleVisible targets=\${targets.size} protected=\${protectedWindows.size}\")\n            setRouting(wanted)")
                    .replace("    private fun captureOriginal(target: CaptureTarget) {", "    private fun captureOriginal(target: CaptureTarget) {\n        android.util.Log.i(\"ScrollProbe\", \"CAPTURE_START \${target.id}\")")
                    .replace("    private fun message(text: String) {", "    private fun message(text: String) {\n        android.util.Log.i(\"ScrollProbe\", \"MESSAGE \$text\")")
            }
            File(destination, name).writeText(source)
        }
    }
}
extensions.configure<ApplicationExtension> {
    sourceSets.getByName("main").java.srcDir(generated)
}
tasks.named("preBuild").configure { dependsOn(prepareProduction) }
