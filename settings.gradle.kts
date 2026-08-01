pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        // Точные версии как у LMG: KGP 2.3.7 допускает kotlin.android поверх
        // AGP 9 (2.3.10 уже запрещает — ошибка "no longer required").
        id("com.google.devtools.ksp") version "2.3.7"
        id("org.jetbrains.kotlin.android") version "2.3.7"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.3.10"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Пропатченный media3 (форк media3-lmg): локальный m2-репозиторий.
        // Content-фильтр — только наша группа, остальное из google()/mavenCentral().
        maven {
            url = uri("media3-m2")
            content { includeGroup("com.liquidmusicglass.media3") }
        }
        google()
        mavenCentral()
        // JitPack: com.github.requery:sqlite-android, com.github.Adonai:jaudiotagger
        maven {
            url = uri("https://jitpack.io")
            content { includeGroupByRegex("com\\.github\\..*") }
        }
    }
}

rootProject.name = "lmg-recovered"
include(":app")
