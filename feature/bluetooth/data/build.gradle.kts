import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("settings.android.library")
    id("settings.android.hilt")
}

android {
    namespace = "com.android.car.settings.feature.bluetooth.data"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":feature:bluetooth:domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.coroutines.android)

    compileOnly(fileTree(rootProject.file("libs/platform")) { include("*.jar") })
    compileOnly(fileTree(rootProject.file("libs/system-server")) { include("*.jar") })

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
}

afterEvaluate {
    tasks.withType<JavaCompile>().configureEach {
        val platformJars = fileTree(rootProject.file("libs/platform")) { include("*.jar") }
        options.bootstrapClasspath = platformJars
        classpath = platformJars + classpath
    }
}
