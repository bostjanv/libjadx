plugins {
    application
}

group = "dev.libjadx"
version = "0.1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("io.github.skylot:jadx-core:1.5.6")
    // jadx-core alone has no input loaders in the standalone distribution.
    // Match the pinned CLI's headless runtime plugin set, without jadx-gui.
    runtimeOnly("io.github.skylot:jadx-analysis:1.5.6")
    // The bounded census uses these already shipped, pinned input readers.
    implementation("io.github.skylot:jadx-dex-input:1.5.6")
    implementation("io.github.skylot:jadx-java-input:1.5.6")
    runtimeOnly("io.github.skylot:jadx-java-convert:1.5.6")
    runtimeOnly("io.github.skylot:jadx-smali-input:1.5.6")
    implementation("io.github.skylot:jadx-rename-mappings:1.5.6")
    implementation("net.fabricmc:mapping-io:0.8.0")
    runtimeOnly("io.github.skylot:jadx-kotlin-metadata:1.5.6")
    runtimeOnly("io.github.skylot:jadx-kotlin-source-debug-extension:1.5.6")
    runtimeOnly("io.github.skylot:jadx-xapk-input:1.5.6")
    runtimeOnly("io.github.skylot:jadx-aab-input:1.5.6")
    runtimeOnly("io.github.skylot:jadx-apkm-input:1.5.6")
    runtimeOnly("io.github.skylot:jadx-apks-input:1.5.6")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("com.google.re2j:re2j:1.8")
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.21.7"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("org.eclipse.jetty:jetty-server:12.1.13")
    implementation("org.eclipse.jetty.ee10:jetty-ee10-servlet:12.1.13")

    testImplementation("io.github.skylot:jadx-gui:1.5.6")
    // Phase 5.2 feasibility only: inspect the pinned loaded tree and codec.
    testImplementation("io.github.skylot:jadx-rename-mappings:1.5.6")
    testImplementation("net.fabricmc:mapping-io:0.8.0")
    testImplementation("com.google.code.gson:gson:2.14.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

dependencyLocking {
    lockAllConfigurations()
}

tasks.test {
    useJUnitPlatform()
    dependsOn(tasks.installDist)
    systemProperty("libjadx.distributionScript", layout.buildDirectory.file("install/libjadx/bin/libjadx").get().asFile.absolutePath)
}

application {
    mainClass.set("dev.libjadx.app.LibJadxMain")
}

distributions {
    main {
        contents {
            from("licenses") { into("licenses") }
        }
    }
}

val guiSavedProject = layout.buildDirectory.file("native-roundtrip-fixture/gui-resaved.jadx")

tasks.register<Exec>("saveNativeProjectWithMatchingGui") {
    dependsOn(tasks.test)
    val guiPath = providers.environmentVariable("JADX_GUI")
    doFirst {
        if (!guiPath.isPresent) {
            throw GradleException("Set JADX_GUI to the matching jadx-gui executable before running guiRoundTripTest")
        }
    }
    commandLine(
        "bash",
        "tests/gui-round-trip.sh",
        guiPath.orNull ?: "",
        layout.buildDirectory.file("native-roundtrip-fixture/sample.jar.jadx").get().asFile.absolutePath,
        guiSavedProject.get().asFile.absolutePath,
    )
}

tasks.register<Test>("guiRoundTripTest") {
    dependsOn("saveNativeProjectWithMatchingGui")
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
        includeTestsMatching("dev.libjadx.probes.NativeGuiReverseRoundTripProbeTest")
    }
    environment("LIBJADX_GUI_SAVED_PROJECT", guiSavedProject.get().asFile.absolutePath)
}

val rawGuiSavedProject = layout.buildDirectory.file("raw-roundtrip-fixture/gui-resaved.jadx")

tasks.register<Exec>("saveRawProjectWithMatchingGui") {
    dependsOn(tasks.test)
    val guiPath = providers.environmentVariable("JADX_GUI")
    doFirst {
        if (!guiPath.isPresent) {
            throw GradleException("Set JADX_GUI to the matching jadx-gui executable before running rawGuiRoundTripTest")
        }
    }
    commandLine(
        "bash",
        "tests/gui-round-trip.sh",
        guiPath.orNull ?: "",
        layout.buildDirectory.file("raw-roundtrip-fixture/raw.jadx").get().asFile.absolutePath,
        rawGuiSavedProject.get().asFile.absolutePath,
    )
}

tasks.register<Test>("rawGuiRoundTripTest") {
    dependsOn("saveRawProjectWithMatchingGui")
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
        includeTestsMatching("dev.libjadx.probes.RawGuiReverseRoundTripTest")
    }
    environment("LIBJADX_RAW_GUI_SAVED_PROJECT", rawGuiSavedProject.get().asFile.absolutePath)
}

val editGuiSavedProject = layout.buildDirectory.file("edit-gui-fixture/gui-resaved.jadx")

tasks.register<Exec>("saveNativeEditsWithMatchingGui") {
    dependsOn(tasks.test)
    val guiPath = providers.environmentVariable("JADX_GUI")
    doFirst {
        if (!guiPath.isPresent) throw GradleException("Set JADX_GUI to the matching jadx-gui executable")
    }
    commandLine(
        "bash", "tests/gui-round-trip.sh", guiPath.orNull ?: "",
        layout.buildDirectory.file("edit-gui-fixture/edited.jadx").get().asFile.absolutePath,
        editGuiSavedProject.get().asFile.absolutePath,
    )
}

tasks.register<Test>("nativeEditGuiRoundTripTest") {
    dependsOn("saveNativeEditsWithMatchingGui")
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter { includeTestsMatching("dev.libjadx.probes.NativeEditGuiReverseRoundTripTest") }
    environment("LIBJADX_EDIT_GUI_SAVED_PROJECT", editGuiSavedProject.get().asFile.absolutePath)
}

val mappingGuiSavedProject = layout.buildDirectory.file("mapping-export-gui-fixture/gui-resaved.jadx")

tasks.register<Exec>("saveExportedMappingsWithMatchingGui") {
    dependsOn(tasks.test)
    val guiPath = providers.environmentVariable("JADX_GUI")
    doFirst {
        if (!guiPath.isPresent) throw GradleException("Set JADX_GUI to the matching jadx-gui executable")
    }
    commandLine(
        "bash", "tests/gui-round-trip.sh", guiPath.orNull ?: "",
        layout.buildDirectory.file("mapping-export-gui-fixture/mapping-copy.jadx").get().asFile.absolutePath,
        mappingGuiSavedProject.get().asFile.absolutePath,
    )
}

tasks.register<Test>("mappingExportGuiRoundTripTest") {
    dependsOn("saveExportedMappingsWithMatchingGui")
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter { includeTestsMatching("dev.libjadx.probes.MappingExportGuiReverseRoundTripTest") }
    environment("LIBJADX_MAPPING_GUI_SAVED_PROJECT", mappingGuiSavedProject.get().asFile.absolutePath)
}

val importGuiSavedProject = layout.buildDirectory.file("mapping-import-gui-fixture/gui-resaved.jadx")

tasks.register<Exec>("saveImportedMappingsWithMatchingGui") {
    dependsOn(tasks.test)
    val guiPath = providers.environmentVariable("JADX_GUI")
    doFirst {
        if (!guiPath.isPresent) throw GradleException("Set JADX_GUI to the matching jadx-gui executable")
    }
    commandLine(
        "bash", "tests/gui-round-trip.sh", guiPath.orNull ?: "",
        layout.buildDirectory.file("mapping-import-gui-fixture/imported.jadx").get().asFile.absolutePath,
        importGuiSavedProject.get().asFile.absolutePath,
    )
}

tasks.register<Test>("mappingImportGuiRoundTripTest") {
    dependsOn("saveImportedMappingsWithMatchingGui")
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter { includeTestsMatching("dev.libjadx.probes.MappingImportGuiReverseRoundTripTest") }
    environment("LIBJADX_IMPORT_GUI_SAVED_PROJECT", importGuiSavedProject.get().asFile.absolutePath)
}

val scopedGuiSavedProject = layout.buildDirectory.file("scoped-edit-gui-fixture/gui-resaved.jadx")

tasks.register<Exec>("saveScopedEditsWithMatchingGui") {
    dependsOn(tasks.test)
    val guiPath = providers.environmentVariable("JADX_GUI")
    doFirst {
        if (!guiPath.isPresent) throw GradleException("Set JADX_GUI to the matching jadx-gui executable")
    }
    commandLine(
        "bash", "tests/gui-round-trip.sh", guiPath.orNull ?: "",
        layout.buildDirectory.file("scoped-edit-gui-fixture/scoped.jadx").get().asFile.absolutePath,
        scopedGuiSavedProject.get().asFile.absolutePath,
    )
}

tasks.register<Test>("scopedEditGuiRoundTripTest") {
    dependsOn("saveScopedEditsWithMatchingGui")
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter { includeTestsMatching("dev.libjadx.probes.ScopedEditGuiReverseRoundTripTest") }
    environment("LIBJADX_SCOPED_GUI_SAVED_PROJECT", scopedGuiSavedProject.get().asFile.absolutePath)
}

// PR #14 evidence-only diagnostics: neither strategy enables API propagation.
val relatedGuiRoot = layout.buildDirectory.dir("related-gui-fixture")
for (strategy in listOf("seed", "members")) {
    tasks.register<Exec>("saveRelated${strategy.replaceFirstChar { it.uppercase() }}WithMatchingGui") {
        dependsOn(tasks.test)
        val guiPath = providers.environmentVariable("JADX_GUI")
        doFirst {
            if (!guiPath.isPresent) throw GradleException("Set JADX_GUI to the matching jadx-gui executable")
        }
        commandLine(
            "bash", "tests/gui-round-trip.sh", guiPath.orNull ?: "",
            relatedGuiRoot.get().file("$strategy/diagnostic.jadx").asFile.absolutePath,
            relatedGuiRoot.get().file("$strategy/gui-resaved.jadx").asFile.absolutePath,
        )
        doLast {
            copy {
                from("/tmp/libjadx-gui-roundtrip.log")
                into(relatedGuiRoot.get().dir(strategy))
                rename { "actual-gui.log" }
            }
        }
    }
}
// The existing GUI harness uses one shared log; serialize these actual GUI runs.
tasks.named("saveRelatedMembersWithMatchingGui") { mustRunAfter("saveRelatedSeedWithMatchingGui") }
tasks.register<Test>("relatedPropagationGuiRoundTripTest") {
    dependsOn("saveRelatedSeedWithMatchingGui", "saveRelatedMembersWithMatchingGui")
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter { includeTestsMatching("dev.libjadx.probes.RelatedPropagationGuiReverseProbeTest") }
    environment("LIBJADX_RELATED_GUI_ROOT", relatedGuiRoot.get().asFile.absolutePath)
}

// PR #16 outcome B: actual service replay negative evidence, not an accepted propagation gate.
val propagatedReplayGuiRoot = layout.buildDirectory.dir("propagated-replay-gui-fixture")
var previousReplayGuiTask: String? = null
for (state in listOf("cold", "hot")) for (position in 0..3) {
    val case = "$state$position"
    val taskName = "savePropagatedReplay${state.replaceFirstChar { it.uppercase() }}${position}WithMatchingGui"
    val previousTask = previousReplayGuiTask
    tasks.register<Exec>(taskName) {
        dependsOn(tasks.test)
        if (previousTask != null) mustRunAfter(previousTask)
        val guiPath = providers.environmentVariable("JADX_GUI")
        doFirst {
            if (!guiPath.isPresent) throw GradleException("Set JADX_GUI to the matching jadx-gui executable")
        }
        commandLine(
            "bash", "tests/gui-round-trip.sh", guiPath.orNull ?: "",
            propagatedReplayGuiRoot.get().file("$case/diagnostic.jadx").asFile.absolutePath,
            propagatedReplayGuiRoot.get().file("$case/gui-resaved.jadx").asFile.absolutePath,
        )
        doLast {
            copy {
                from("/tmp/libjadx-gui-roundtrip.log")
                into(propagatedReplayGuiRoot.get().dir(case))
                rename { "actual-gui.log" }
            }
        }
    }
    previousReplayGuiTask = taskName
}
tasks.register<Test>("propagatedEditReplayGuiDiagnosticTest") {
    dependsOn((listOf("cold", "hot").flatMap { state -> (0..3).map { "savePropagatedReplay${state.replaceFirstChar { it.uppercase() }}${it}WithMatchingGui" } }))
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter { includeTestsMatching("dev.libjadx.app.PropagatedNativeReplayTest.actualMatchingGuiConfirmsMemberPersistenceAndHotNonmemberDisagreement") }
    environment("LIBJADX_PROPAGATED_REPLAY_GUI_ROOT", propagatedReplayGuiRoot.get().asFile.absolutePath)
}
