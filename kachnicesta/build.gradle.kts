import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cz.kachnicesta"
    compileSdk = 36

    defaultConfig {
        applicationId = "cz.kachnicesta"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "2.0"
    }

    // Pevný klíč uložený v repozitáři, aby šla každá nová verze nainstalovat
    // přes tu předchozí.
    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            storePassword = "kachnicesta"
            keyAlias = "kachnicesta"
            keyPassword = "kachnicesta"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
