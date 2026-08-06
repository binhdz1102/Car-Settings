plugins {
    id("settings.android.library")
    id("settings.android.hilt")
}

android {
    namespace = "com.android.car.settings.feature.search.data"
}

dependencies {
    implementation(project(":feature:search:domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
