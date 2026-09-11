plugins {
    id("settings.android.library")
    id("settings.android.navigation.compose")
    id("settings.android.hilt")
    alias(libs.plugins.bmaterial.aosp.platform.stubs)
}

aospPlatformStubs {
    includeSystemServer.set(true)
}

android {
    namespace = "com.android.car.settings.feature.bluetooth"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.coroutines.core)
    implementation(libs.timber)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
}
