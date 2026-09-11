import java.util.Properties

plugins {
    id("settings.android.application")
    id("settings.android.navigation.compose")
    id("settings.android.hilt")
    alias(libs.plugins.bmaterial.aosp.platform.stubs)
}

aospPlatformStubs {
    includeSystemServer.set(true)
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
    implementation(project(":core:vehicle"))
    implementation(project(":core:settings-api"))
    implementation(libs.bmaterial.ccp.rotary.focus)
    implementation(project(":feature:wifi"))
    implementation(project(":feature:bluetooth"))
    implementation(project(":feature:sound"))
    implementation(project(":feature:display"))
    implementation(project(":feature:applications"))
    implementation(project(":feature:profileaccounts"))
    implementation(project(":feature:system"))
    implementation(project(":feature:notifications"))
    implementation(project(":feature:privacy"))
    implementation(project(":feature:security"))
    implementation(project(":feature:search"))
    implementation(project(":feature:hvac"))
    implementation(project(":feature:accessibility"))
    implementation(project(":feature:location"))
    implementation(project(":feature:assistantvoice"))
    implementation(project(":feature:doorcontrol"))
    implementation(project(":feature:seatcontrol"))
    implementation(project(":feature:vehiclelighting"))
    implementation(project(":feature:driverassistance"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.coroutines.android)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.material)
    implementation(libs.timber)
    // Hilt's generated view-model key maps reference Guava collections at runtime.
    implementation(libs.guava.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
