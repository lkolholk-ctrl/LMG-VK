plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization")
}

android {
    namespace = "com.lmg.vk"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lmg.vk"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0" // собственная нумерация LMG VK

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        debug {
            isMinifyEnabled = false
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDir("src/main/kotlin")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

configurations.configureEach {
    // Любая зависимость, притащившая оригинальный media3, отсекается: форк живёт
    // в тех же Java-пакетах (androidx.media3.*), и две копии классов ломают сборку.
    exclude(group = "androidx.media3")
}

dependencies {
    // --- корoutines / flow ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // --- сеть: Ktor Client (как в оригинале) ---
    implementation("io.ktor:ktor-client-core:3.0.3")
    implementation("io.ktor:ktor-client-okhttp:3.0.3")

    // --- JSON: Moshi (как в оригинале) ---
    implementation("com.squareup.moshi:moshi:1.15.1")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    // --- плеер: media3 из НАШЕГО форка (media3-lmg, 1.5.1-lmg29) ---
    // Java-пакеты оригинала (androidx.media3.*) -> восстановленный код не меняется.
    implementation("com.liquidmusicglass.media3:media3-common:1.5.1-lmg29")
    implementation("com.liquidmusicglass.media3:media3-exoplayer:1.5.1-lmg29")
    // HLS — VK отдаёт часть аудио/клипов .m3u8-потоками
    implementation("com.liquidmusicglass.media3:media3-exoplayer-hls:1.5.1-lmg29")
    implementation("com.liquidmusicglass.media3:media3-extractor:1.5.1-lmg29")
    implementation("com.liquidmusicglass.media3:media3-session:1.5.1-lmg29")
    implementation("com.liquidmusicglass.media3:media3-common-ktx:1.5.1-lmg29")
    implementation("com.liquidmusicglass.media3:media3-ui:1.5.1-lmg29")

    // --- кэш: Realm (librealmc.so в оригинале) ---
    implementation("io.realm.kotlin:library-base:2.3.0")

    // --- UI-основа ---
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
