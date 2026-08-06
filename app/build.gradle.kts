import java.util.Properties

plugins {
    id("settings.android.application")
    id("settings.android.navigation.compose")
    id("settings.android.hilt")
}

val gitCommitHash =
    providers
        .exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
        }.standardOutput
        .asText
        .map { value -> value.trim().ifEmpty { "unknown" } }
        .getOrElse("unknown")

val platformKeystoreProperties =
    Properties().apply {
        rootProject.file("keystore.properties").inputStream().use(::load)
    }

android {
    namespace = "com.android.car.settings"

    defaultConfig {
        applicationId = "com.android.car.settings"
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "GIT_COMMIT_HASH", "\"$gitCommitHash\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("platform") {
            storeFile =
                rootProject.file(
                    platformKeystoreProperties.getProperty("storeFile"),
                )
            storePassword = platformKeystoreProperties.getProperty("storePassword")
            keyAlias = platformKeystoreProperties.getProperty("keyAlias")
            keyPassword = platformKeystoreProperties.getProperty("keyPassword")
            storeType = platformKeystoreProperties.getProperty("storeType")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("platform")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("platform")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":feature:wifi:data"))
    implementation(project(":feature:wifi:domain"))
    implementation(project(":feature:wifi:presentation"))
    implementation(project(":feature:bluetooth:data"))
    implementation(project(":feature:bluetooth:domain"))
    implementation(project(":feature:bluetooth:presentation"))
    implementation(project(":feature:sound:data"))
    implementation(project(":feature:sound:domain"))
    implementation(project(":feature:sound:presentation"))
    implementation(project(":feature:display:data"))
    implementation(project(":feature:display:domain"))
    implementation(project(":feature:display:presentation"))
    implementation(project(":feature:applications:data"))
    implementation(project(":feature:applications:domain"))
    implementation(project(":feature:applications:presentation"))
    implementation(project(":feature:profileaccounts:data"))
    implementation(project(":feature:profileaccounts:domain"))
    implementation(project(":feature:profileaccounts:presentation"))
    implementation(project(":feature:system:data"))
    implementation(project(":feature:system:domain"))
    implementation(project(":feature:system:presentation"))
    implementation(project(":feature:notifications:data"))
    implementation(project(":feature:notifications:domain"))
    implementation(project(":feature:notifications:presentation"))
    implementation(project(":feature:privacy:data"))
    implementation(project(":feature:privacy:domain"))
    implementation(project(":feature:privacy:presentation"))
    implementation(project(":feature:security:data"))
    implementation(project(":feature:security:domain"))
    implementation(project(":feature:security:presentation"))
    implementation(project(":feature:search:data"))
    implementation(project(":feature:search:domain"))
    implementation(project(":feature:search:presentation"))
    implementation(project(":feature:hvac:data"))
    implementation(project(":feature:hvac:domain"))
    implementation(project(":feature:hvac:presentation"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.coroutines.android)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.material)
    implementation(libs.timber)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
