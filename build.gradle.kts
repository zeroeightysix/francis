import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.10"
    application
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "me.zeroeightsix.francis.bot"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven(url = "https://libraries.minecraft.net")
}

application {
    mainClass.set("me.zeroeightsix.francis.MainKt")
}

tasks {
    named<ShadowJar>("shadowJar") {
        archiveBaseName.set("shadow")
        mergeServiceFiles()
        manifest {
            attributes(mapOf("Main-Class" to application.mainClass.get()))
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

dependencies {
    implementation("com.rabbitmq:amqp-client:5.14.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.0.4")
    implementation("ch.qos.logback:logback-classic:1.2.11")
    implementation("com.mojang:brigadier:1.0.18")
}
