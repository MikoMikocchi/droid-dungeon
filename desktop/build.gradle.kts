plugins {
    application
}

val gdxVersion: String by project
val lwjglVersionOverride = "3.3.6"

dependencies {
    implementation(project(":core"))

    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-freetype:$gdxVersion")
    implementation("org.java-websocket:Java-WebSocket:1.5.6")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:2.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names:2.17.2")
}

application {
    mainClass.set("com.droiddungeon.desktop.DesktopLauncher")
    applicationDefaultJvmArgs += listOf(
        "--enable-native-access=ALL-UNNAMED"
    )
}

// Propagate project properties to JVM system properties for run task (network flags etc.)
tasks.named<JavaExec>("run") {
    listOf("network", "network.host", "network.port").forEach { key ->
        val value = project.findProperty(key)?.toString()
        if (value != null) {
            systemProperty(key, value)
        }
    }
    // Required by JDK 24+ to allow native library loads without warnings
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

sourceSets {
    named("main") {
        resources.srcDir(file("../assets"))
    }
}

tasks.withType<JavaExec>().configureEach {
    // Требуется на macOS для LWJGL3.
    jvmArgs("-XstartOnFirstThread")
    workingDir = file("../assets")
}

configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.lwjgl") {
            useVersion(lwjglVersionOverride)
            because("JDK 25 compatibility and updated JNI version support in LWJGL 3.3.6")
        }
    }
}
