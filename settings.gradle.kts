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
include(":feature:wifi:domain")
include(":feature:wifi:data")
include(":feature:wifi:presentation")
include(":feature:bluetooth:domain")
include(":feature:bluetooth:data")
include(":feature:bluetooth:presentation")
include(":feature:sound:domain")
include(":feature:sound:data")
include(":feature:sound:presentation")
include(":feature:display:domain")
include(":feature:display:data")
include(":feature:display:presentation")
include(":feature:applications:domain")
include(":feature:applications:data")
include(":feature:applications:presentation")
include(":feature:profileaccounts:domain")
include(":feature:profileaccounts:data")
include(":feature:profileaccounts:presentation")
include(":feature:system:domain")
include(":feature:system:data")
include(":feature:system:presentation")
include(":feature:notifications:domain")
include(":feature:notifications:data")
include(":feature:notifications:presentation")
include(":feature:privacy:domain")
include(":feature:privacy:data")
include(":feature:privacy:presentation")
include(":feature:security:domain")
include(":feature:security:data")
include(":feature:security:presentation")
include(":feature:search:domain")
include(":feature:search:data")
include(":feature:search:presentation")
include(":feature:hvac:domain")
include(":feature:hvac:data")
include(":feature:hvac:presentation")
