plugins {
    kotlin("jvm") version "2.2.10"
    application
}

group = "com.bikepacking.karoo"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("com.garmin:fit:21.141.0")
    implementation("com.google.code.gson:gson:2.11.0")
}

application {
    mainClass.set("com.bikepacking.karoo.fitexport.MainKt")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "com.bikepacking.karoo.fitexport.MainKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
