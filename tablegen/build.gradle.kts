import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

application {
    mainClass = "com.fujitake.cribbage.tablegen.MainKt"
}

dependencies {
    implementation(project(":engine"))
}

tasks.named<JavaExec>("run") {
    workingDir = rootDir
}
