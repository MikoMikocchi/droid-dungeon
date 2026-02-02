plugins {
    scala
    application
}

repositories {
    mavenCentral()
}

val scalaVersion = "3.8.1"
val pekkoVersion = "1.3.0"

dependencies {
    implementation(project(":core"))

    implementation("org.scala-lang:scala3-library_3:$scalaVersion")
    implementation("org.apache.pekko:pekko-actor-typed_3:$pekkoVersion")
    implementation("org.apache.pekko:pekko-stream_3:$pekkoVersion")
    implementation("org.apache.pekko:pekko-stream-typed_3:$pekkoVersion")
    implementation("org.apache.pekko:pekko-http_3:$pekkoVersion")
    implementation("org.apache.pekko:pekko-serialization-jackson_3:$pekkoVersion")
    implementation("org.apache.pekko:pekko-http-spray-json_3:$pekkoVersion")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("com.typesafe:config:1.4.3")
}

application {
    mainClass.set("com.droiddungeon.server.HttpServer")
}

tasks.withType<ScalaCompile>().configureEach {
    scalaCompileOptions.apply {
        additionalParameters = listOf("-deprecation", "-unchecked")
    }
}

sourceSets {
    named("main") {
        resources.srcDir(file("../assets"))
    }
}
