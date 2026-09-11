plugins {
    id("settings.android.library")
    id("settings.android.hilt")
    alias(libs.plugins.bmaterial.aosp.platform.stubs)
}

android {
    namespace = "com.android.car.settings.core.vehicle"
}

dependencies {
    implementation(project(":core:common"))

    implementation(libs.coroutines.core)
    implementation(libs.dagger.hilt.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
}
