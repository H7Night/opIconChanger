plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "2.1.20-1.0.31"
}

// release 签名密钥不落库：
// - 本地只构建 debug（AGP 默认使用 ~/.android/debug.keystore，开发者自行签名）
// - CI（GitHub Actions）先解码 Secrets 到文件，再注入 RELEASE_KEYSTORE_FILE / RELEASE_KEYSTORE_PASS / RELEASE_KEYSTORE_ALIAS
// 未提供密钥时 release 构建不签名（由发布方自行处理），保证仓库内无密钥依赖。
val releaseKeystoreFile: String? = System.getenv("RELEASE_KEYSTORE_FILE") ?: project.findProperty("RELEASE_KEYSTORE_FILE") as String?
val releaseKeystorePass: String? = System.getenv("RELEASE_KEYSTORE_PASS") ?: project.findProperty("RELEASE_KEYSTORE_PASS") as String?
val releaseKeystoreAlias: String = System.getenv("RELEASE_KEYSTORE_ALIAS") ?: project.findProperty("RELEASE_KEYSTORE_ALIAS") as String? ?: "opiconchanger"

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
            if (releaseKeystoreFile != null && releaseKeystorePass != null) {
                storeFile = File(releaseKeystoreFile)
                storePassword = releaseKeystorePass
                keyAlias = releaseKeystoreAlias
                keyPassword = releaseKeystorePass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 仅当 CI/环境提供了密钥时才签名；否则保持未签名（由发布方自行处理）
            signingConfig = signingConfigs.findByName("release")?.takeIf {
                releaseKeystoreFile != null && releaseKeystorePass != null
            }
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

    // Material Components (Material3 主题与组件，仅引入用到的部分)
    implementation("com.google.android.material:material:1.12.0")

    // Unit tests (pure JVM logic only)
    testImplementation("junit:junit:4.13.2")
    // org.json: 真机由 Android 框架提供；单元测试中 Android 版被 mock（方法抛异常），
    // 引入真实实现使 IconRequest 的 JSON 序列化/校验可测。
    testImplementation("org.json:json:20240303")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // KavaRef (reflection API, 替代 YukiHookAPI 废弃的反射方法)
    implementation("com.highcapable.kavaref:kavaref-core:1.1.0")
    implementation("com.highcapable.kavaref:kavaref-android:1.1.0")
    implementation("com.highcapable.kavaref:kavaref-extension:1.1.0")
}
