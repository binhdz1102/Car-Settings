plugins {
    id("settings.android.library")
    id("settings.android.compose")
}

android {
    namespace = "com.b231001.bmaterial.ccp.rotaryfocus"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            proguardFiles("proguard-rules.pro")
        }
    }
}

dependencies {
    // These types are part of the rotary module's public API, matching the upstream AAR.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.activity.compose)
    api(libs.androidx.compose.ui)
    api("androidx.compose.runtime:runtime")
    api("androidx.compose.foundation:foundation")

    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.ktx)

    debugImplementation(libs.androidx.navigation.compose)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit)
}

// Preserve vendored upstream source except for the documented touch-mode focus hand-off and
// settled-geometry validation in NOTICE. First-party style rules are intentionally not imposed on
// third-party code; compilation and upstream unit tests remain part of verification.
tasks.configureEach {
    if (name == "detekt" || name.contains("Ktlint", ignoreCase = true)) {
        enabled = false
    }
}
