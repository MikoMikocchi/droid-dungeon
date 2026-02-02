import org.gradle.api.plugins.JavaPluginExtension

plugins {
    id("com.diffplug.spotless") version "8.2.1"
}

allprojects {
    group = "com.droiddungeon"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "com.diffplug.spotless")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    spotless {
        java {
            target("src/**/*.java")
            googleJavaFormat("1.33.0")
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    dependencies {
        add("testImplementation", platform("org.junit:junit-bom:5.10.1"))
        add("testImplementation", "org.junit.jupiter:junit-jupiter")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.named("check").configure {
        dependsOn(tasks.named("spotlessCheck"))
    }
}
