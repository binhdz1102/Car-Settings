import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("settings.android.library")
    id("settings.android.navigation.compose")
    id("settings.android.hilt")
}

android { namespace = "com.android.car.settings.feature.vehiclelighting" }

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:vehicle"))
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.coroutines.core)
    compileOnly(fileTree(rootProject.file("libs/platform")) { include("*.jar") })
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
}

afterEvaluate {
    tasks.withType<JavaCompile>().configureEach {
        val platformJars = fileTree(rootProject.file("libs/platform")) { include("*.jar") }
        options.bootstrapClasspath = platformJars
        classpath = platformJars + classpath
    }
}
