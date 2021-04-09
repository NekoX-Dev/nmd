import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val logback_version: String by project
val ktor_version: String by project
val kotlin_version: String by project

plugins {
    application
    java
    kotlin("jvm") version "1.4.32"
}

group = "io.nekohasekai"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

repositories {
    mavenLocal()
    jcenter()
    maven { url = uri("https://kotlin.bintray.com/ktor") }
}


val compileKotlin: KotlinCompile by tasks

compileKotlin.kotlinOptions {
    jvmTarget = "11"
}


dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlin_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-websockets:$ktor_version")

    implementation("io.ktor:ktor-gson:$ktor_version")
    implementation("org.bouncycastle:bcprov-jdk15on:1.68")
    implementation("org.bouncycastle:bcprov-ext-jdk15on:1.68")

    implementation("org.apache.commons:commons-crypto:1.1.0")

    val vHutool = "5.5.8"
    implementation("cn.hutool:hutool-crypto:$vHutool")

    val vKtLib = "1.0-SNAPSHOT"
    implementation("io.nekohasekai.ktlib:ktlib-td-cli:$vKtLib")
    implementation("io.nekohasekai.ktlib:ktlib-db:$vKtLib")

    implementation("org.slf4j:slf4j-simple:2.0.0-alpha1")
}

kotlin.sourceSets["main"].kotlin.srcDirs("src")
sourceSets["main"].java.srcDirs("src")
sourceSets["main"].resources.srcDirs("resources")
