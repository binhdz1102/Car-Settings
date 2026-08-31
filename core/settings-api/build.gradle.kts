plugins {
    id("settings.android.library")
}

android {
    namespace = "com.android.car.settings.core.settings"
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
