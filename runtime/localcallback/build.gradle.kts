plugins {
    id("settings.android.library")
}

android {
    namespace = "com.b231001.bmaterial.runtime.localcallback"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    api(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlinx.coroutines.test)

//    androidTestImplementation(libs.androidx.test.ext.junit)
//    androidTestImplementation(libs.androidx.test.runner)
//    androidTestImplementation(libs.kotlinx.coroutines.test)
}
