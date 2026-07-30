pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
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
    }
}

rootProject.name = "lmg-recovered"
include(":app")
