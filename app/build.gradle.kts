plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("plugin.serialization") version "2.3.10"
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.lmg.vk"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.lmg.vk"
        minSdk = 29
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

    // Секреты подписи: из env (CI) или local.properties (локально). Не хардкодить.
    fun signingSecret(envName: String, localPropName: String): String {
        System.getenv(envName)?.takeIf { it.isNotBlank() }?.let { return it }
        val f = rootProject.file("local.properties")
        if (f.exists()) {
            val prefix = "$localPropName="
            f.readLines().find { it.startsWith(prefix) }
                ?.removePrefix(prefix)?.trim()
                ?.let { return it }
        }
        return ""
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "release-key.jks")
            storePassword = signingSecret("KEYSTORE_PASSWORD", "KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "release"
            keyPassword = signingSecret("KEY_PASSWORD", "KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
        release {
            // На первых сборках без R8: сначала добиваемся компиляции,
            // обфускацию включаем отдельным этапом (proguard-rules.pro).
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            isJniDebuggable = false
            vcsInfo.include = false
            signingConfig = if (file(System.getenv("KEYSTORE_PATH") ?: "release-key.jks").exists()) {
                signingConfigs.getByName("release")
            } else {
                null // unsigned release — ворнинг в CI
            }
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
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xlambdas=class")
    }
}

configurations.configureEach {
    // Оригинальный media3 отсекается: форк живёт в тех же Java-пакетах
    // (androidx.media3.*), две копии классов ломают сборку.
    exclude(group = "androidx.media3")
}

dependencies {
    // --- корoutines / flow ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // --- сеть: Ktor Client (как в оригинале VK X) ---
    implementation("io.ktor:ktor-client-core:3.0.3")
    implementation("io.ktor:ktor-client-okhttp:3.0.3")

    // --- JSON: Moshi (network DTO) + kotlinx.serialization (backend models) ---
    implementation("com.squareup.moshi:moshi:1.15.1")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    // --- плеер: media3 из НАШЕГО форка (media3-lmg, 1.5.1-lmg29) ---
    implementation("com.liquidmusicglass.media3:media3-common:1.5.1-lmg29")
    implementation("com.liquidmusicglass.media3:media3-exoplayer:1.5.1-lmg29")
    implementation("com.liquidmusicglass.media3:media3-exoplayer-hls:1.5.1-lmg29")
    implementation("com.liquidmusicglass.media3:media3-extractor:1.5.1-lmg29")
    implementation("com.liquidmusicglass.media3:media3-session:1.5.1-lmg29")
    implementation("com.liquidmusicglass.media3:media3-common-ktx:1.5.1-lmg29")
    implementation("com.liquidmusicglass.media3:media3-ui:1.5.1-lmg29")

    // --- кэш загрузок: Realm ---
    implementation("io.realm.kotlin:library-base:2.3.0")

    // --- UI-основа ---
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")

    // --- Compose (UI из LMG) ---
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material:material-ripple")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // liquid glass стек (как в LMG)
    implementation("io.github.kyant0:backdrop:2.0.0-alpha03")
    implementation("io.github.kyant0:shapes:1.2.0")
    implementation("io.github.kyant0:capsule:2.1.3")
    implementation("io.github.kyant0:fishnet:1.1.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")

    // OkHttp (Coil image loader в LmgApplication)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Room (избранное/история — data/local/db)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
