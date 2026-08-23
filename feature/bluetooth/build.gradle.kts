import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("settings.android.library")
    id("settings.android.navigation.compose")
    id("settings.android.hilt")
}

android {
    namespace = "com.android.car.settings.feature.bluetooth"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.coroutines.core)
    implementation(libs.timber)
    implementation(libs.coroutines.android)

    compileOnly(fileTree(rootProject.file("libs/platform")) { include("*.jar") })
    compileOnly(fileTree(rootProject.file("libs/system-server")) { include("*.jar") })

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
}

afterEvaluate {
    tasks.withType<JavaCompile>().configureEach {
        val platformJars = fileTree(rootProject.file("libs/platform")) { include("*.jar") }
        options.bootstrapClasspath = platformJars
        classpath = platformJars + classpath
    }
}
