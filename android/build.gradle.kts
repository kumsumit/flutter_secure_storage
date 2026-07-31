plugins {
    id("com.android.library")
}

group = "com.it_nomads.fluttersecurestorage"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
}

android {
    namespace = "com.it_nomads.fluttersecurestorage"
    ndkVersion = "30.0.15729638"

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

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    implementation("com.google.crypto.tink:tink-android:1.23.0")
}
