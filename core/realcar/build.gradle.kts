import java.util.Properties

plugins {
    id("settings.android.library")
    id("settings.android.hilt")
}

android {
    namespace = "com.example.myapplication.core.realcar"
}

val localProperties =
    Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.isFile) {
            localPropertiesFile.inputStream().use(::load)
        }
    }

val androidSdkDirProvider =
    providers
        .environmentVariable("ANDROID_HOME")
        .orElse(providers.environmentVariable("ANDROID_SDK_ROOT"))
        .orElse(
            providers.provider {
                checkNotNull(localProperties.getProperty("sdk.dir")) {
                    "ANDROID_HOME, ANDROID_SDK_ROOT, or sdk.dir in local.properties is required for android.car.jar"
                }
            },
        )

val androidCarJarProvider =
    androidSdkDirProvider.map { sdkDir ->
        file("$sdkDir/platforms/android-36/optional/android.car.jar")
    }

dependencies {
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)
    implementation(libs.timber)

    compileOnly(files(androidCarJarProvider))
}
