plugins {
    id("settings.android.library")
    id("settings.android.hilt")
}

android {
    namespace = "com.example.myapplication.core.realcar"
    useLibrary("android.car")
}

dependencies {
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)
    implementation(libs.timber)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
