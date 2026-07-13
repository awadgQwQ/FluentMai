import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    val frameworkName = "FluentMaiShared"
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = frameworkName
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir("src/main/kotlin")
        }
        commonTest {
            kotlin.srcDir("src/test/kotlin")
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

tasks.register("test") {
    dependsOn("jvmTest")
}
