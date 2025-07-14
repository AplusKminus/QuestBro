plugins {
    kotlin("multiplatform") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
    kotlin("plugin.compose") version "2.0.20"
    id("org.jetbrains.compose") version "1.6.11"
}

group = "com.questbro"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

kotlin {
    jvm("desktop")
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "questbro.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
            }
        }
        
        val jsMain by getting {
            dependencies {
                implementation(compose.html.core)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.questbro.desktop.MainKt"
        
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, 
                         org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, 
                         org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "QuestBro"
            packageVersion = "1.0.0"
        }
    }
}

