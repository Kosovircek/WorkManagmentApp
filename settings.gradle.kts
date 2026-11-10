/*
 * settings.gradle.kts – nastavitve Gradle projekta.
 *
 * Tu povemo, iz katerih repozitorijev se prenašajo vtičniki (plugins) in knjižnice
 * ter kateri moduli sestavljajo projekt (samo ":app").
 */
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

rootProject.name = "SluzbaTaski"
include(":app")
