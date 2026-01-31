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
}

application {
    mainClass.set("com.droiddungeon.desktop.DesktopLauncher")
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
