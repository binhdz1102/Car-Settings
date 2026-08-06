plugins {
    id("settings.android.library")
    id("settings.android.navigation.compose")
    id("settings.android.hilt")
}

android {
    namespace = "com.android.car.settings.feature.hvac.presentation"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":feature:hvac:domain"))
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
}
