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
        exclusiveContent {
            forRepository {
                maven {
                    name = "BundledBMaterial"
                    url = uri("$rootDir/libs/bmaterial/maven")
                    metadataSources {
                        gradleMetadata()
                        mavenPom()
                        artifact()
                    }
                }
            }
            filter {
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
include(":third_party:bmaterial-ccp-rotary-focus")
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
include(":feature:location")
include(":feature:assistantvoice")
include(":feature:accessibility")
