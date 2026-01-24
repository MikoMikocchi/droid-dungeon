plugins {
    `java-library`
}

val gdxVersion: String by project

dependencies {
    api("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-freetype:$gdxVersion")
}
