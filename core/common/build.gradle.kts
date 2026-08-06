plugins {
    id("settings.android.library")
    id("settings.android.hilt")
}

android {
    namespace = "com.android.car.settings.core.common"
}

dependencies {
    implementation(libs.coroutines.core)
}
