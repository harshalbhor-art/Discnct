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
        google()
        mavenCentral()
    }
}

rootProject.name = "Discnct"
include(":app")

// game-logic is a standalone Gradle build (its own settings.gradle.kts) so it can be built
// and tested with plain Kotlin + Maven Central alone, without ever touching the Android
// Gradle Plugin — which needs google() and isn't reachable in every sandbox/CI environment.
includeBuild("game-logic")
