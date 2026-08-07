pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CarSettings"
include(":app")
include(":core:common")
include(":core:ui")
include(":feature:wifi")
include(":feature:bluetooth")
include(":feature:sound")
include(":feature:display")
include(":feature:applications")
include(":feature:profileaccounts")
include(":feature:system")
include(":feature:notifications")
include(":feature:privacy")
include(":feature:security")
include(":feature:search")
include(":feature:hvac")
