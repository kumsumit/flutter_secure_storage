import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

group = "com.it_nomads.fluttersecurestorage"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
}

android {
    namespace = "com.it_nomads.fluttersecurestorage"

    buildFeatures {
        buildConfig = true
    }

    compileSdk = 37

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    defaultConfig {
        minSdk = 24
    }
}

dependencies {
    implementation("com.google.crypto.tink:tink-android:1.18.0")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}
