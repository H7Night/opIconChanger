plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "2.1.20-1.0.31"
}

android {
    namespace = "com.opiconchanger"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.opiconchanger"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore/debug.jks")
            storePassword = "android"
            keyAlias = "opiconchanger"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.1.20")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.1.20")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.20")
        force("org.jetbrains.kotlin:kotlin-reflect:2.1.20")
        force("androidx.core:core:1.13.1")
        force("androidx.core:core-ktx:1.13.1")
    }
}

dependencies {
    // Xposed API
    compileOnly("de.robv.android.xposed:api:82")

    // YukiHookAPI 1.3.2 (needs KSP)
    implementation("com.highcapable.yukihookapi:api:1.3.2")
    ksp("com.highcapable.yukihookapi:ksp-xposed:1.3.2")

    // AndroidX
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // KavaRef (reflection API, 替代 YukiHookAPI 废弃的反射方法)
    implementation("com.highcapable.kavaref:kavaref-core:1.1.0")
    implementation("com.highcapable.kavaref:kavaref-android:1.1.0")
    implementation("com.highcapable.kavaref:kavaref-extension:1.1.0")
}
