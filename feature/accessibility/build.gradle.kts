plugins {
    id("settings.android.library")
    id("settings.android.navigation.compose")
    id("settings.android.hilt")
    alias(libs.plugins.bmaterial.aosp.platform.stubs)
}

android {
    namespace = "com.android.car.settings.feature.accessibility"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
}
