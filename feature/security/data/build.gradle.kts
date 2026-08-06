import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("settings.android.library")
    id("settings.android.hilt")
}

afterEvaluate {
    tasks.withType<JavaCompile>().configureEach {
        val platformJars = fileTree(rootProject.file("libs/platform")) { include("*.jar") }
        options.bootstrapClasspath = platformJars
        classpath = platformJars + classpath
    }
}

android {
    namespace = "com.android.car.settings.feature.security.data"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":feature:security:domain"))
    implementation(libs.coroutines.android)
    compileOnly(fileTree(rootProject.file("libs/platform")) { include("*.jar") })
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
}
