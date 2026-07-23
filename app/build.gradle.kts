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

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
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
