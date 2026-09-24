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
    implementation("com.google.code.gson:gson:2.14.0")
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.21.7"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("org.eclipse.jetty:jetty-server:12.1.13")
    implementation("org.eclipse.jetty.ee10:jetty-ee10-servlet:12.1.13")

    testImplementation("io.github.skylot:jadx-gui:1.5.6")
    testImplementation("com.google.code.gson:gson:2.14.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

dependencyLocking {
    lockAllConfigurations()
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("dev.libjadx.app.LibJadxMain")
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
