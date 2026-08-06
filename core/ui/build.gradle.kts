plugins {
    id("settings.android.library")
    id("settings.android.compose")
}

android {
    namespace = "com.android.car.settings.core.ui"
}

dependencies {
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
