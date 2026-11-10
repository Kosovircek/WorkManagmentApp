/*
 * app/build.gradle.kts – nastavitve modula aplikacije.
 *
 * Tehnologije:
 *  - Kotlin + Jetpack Compose (uporabniški vmesnik v aktivnostih)
 *  - Room (lokalna SQLite baza, kjer je VSE trajno shranjeno)
 *  - Glance (widgeti na domačem zaslonu, napisani v Compose slogu)
 *  - KSP (generiranje Room kode ob prevajanju)
 */
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "si.sluzbataski"
    compileSdk = 34

    defaultConfig {
        applicationId = "si.sluzbataski"
        minSdk = 26          // Android 8.0 – potrebno za java.time in adaptivne ikone
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            // Za osebno uporabo pustimo kodo neobfuscirano (lažje razhroščevanje).
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Osnova AndroidX
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Jetpack Compose (verzije poravna BOM)
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Room – baza podatkov
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Glance – widgeti
    implementation("androidx.glance:glance-appwidget:1.1.1")
}
