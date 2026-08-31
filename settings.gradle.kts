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

val bMaterialGitHubUser = providers.gradleProperty("gpr.user")
    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
val bMaterialGitHubKey = providers.gradleProperty("gpr.key")
    .orElse(providers.environmentVariable("GITHUB_TOKEN"))

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            name = "BMaterialGitHubPackages"
            url = uri("https://maven.pkg.github.com/binhdz1102/B-Material")
            credentials {
                username = bMaterialGitHubUser.orNull
                password = bMaterialGitHubKey.orNull
            }
            content {
                includeGroup("com.b231001.bmaterial")
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "CarSettings"
include(":app")
include(":core:common")
include(":core:ui")
include(":core:vehicle")
include(":core:settings-api")
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
include(":feature:doorcontrol")
include(":feature:seatcontrol")
include(":feature:vehiclelighting")
include(":feature:driverassistance")
include(":feature:location")
include(":feature:assistantvoice")
include(":feature:accessibility")
