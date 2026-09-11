// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("settings.root.ktlint")
    id("settings.root.detekt")
    id("settings.root.jacoco")
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.bmaterial.aosp.platform.stubs) apply false
}