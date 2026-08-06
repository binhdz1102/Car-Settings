plugins {
    id("settings.android.library")
}

android {
    namespace = "com.android.car.settings.feature.sound.domain"
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
