plugins {
    application
}

val gdxVersion: String by project

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
}

// Propagate project properties to JVM system properties for run task (network flags etc.)
tasks.named<JavaExec>("run") {
    listOf("network", "network.host", "network.port").forEach { key ->
        val value = project.findProperty(key)?.toString()
        if (value != null) {
            systemProperty(key, value)
        }
    }
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
