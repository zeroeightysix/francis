import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.10"
    application
}

group = "me.ridan"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven(url = "https://libraries.minecraft.net")
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

application {
    mainClass.set("me.zeroeightsix.francis.MainKt")
}