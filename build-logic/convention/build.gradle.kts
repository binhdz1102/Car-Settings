plugins {
    `kotlin-dsl`
}

group = "com.android.car.settings.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.compose.compiler.gradle.plugin)
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.hilt.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.androidx.room.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "settings.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "settings.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "settings.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidCompose") {
            id = "settings.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidNavigationCompose") {
            id = "settings.android.navigation.compose"
            implementationClass = "AndroidNavigationComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "settings.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidKtlint") {
            id = "settings.android.ktlint"
            implementationClass = "AndroidKtlintConventionPlugin"
        }
        register("androidDetekt") {
            id = "settings.android.detekt"
            implementationClass = "AndroidDetektConventionPlugin"
        }
        register("androidJacoco") {
            id = "settings.android.jacoco"
            implementationClass = "AndroidJacocoConventionPlugin"
        }
        register("androidRoom") {
            id = "settings.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("rootKtlint") {
            id = "settings.root.ktlint"
            implementationClass = "RootKtlintConventionPlugin"
        }
        register("rootDetekt") {
            id = "settings.root.detekt"
            implementationClass = "RootDetektConventionPlugin"
        }
        register("rootJacoco") {
            id = "settings.root.jacoco"
            implementationClass = "RootJacocoConventionPlugin"
        }
    }
}
