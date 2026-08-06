plugins {
    id("settings.android.library")
}

android {
    namespace = "com.android.car.settings.feature.search.domain"
}

dependencies {
    implementation(libs.dagger.hilt.android)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
}
