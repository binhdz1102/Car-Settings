plugins {
    id("settings.android.library")
    id("settings.android.navigation.compose")
    id("settings.android.hilt")
    alias(libs.plugins.bmaterial.aosp.platform.stubs)
}

android { namespace = "com.android.car.settings.feature.seatcontrol" }

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:vehicle"))
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
}
