pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.25" apply false
}

rootProject.name = "flutter_secure_storage"
