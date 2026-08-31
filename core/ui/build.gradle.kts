plugins {
    id("settings.android.library")
    id("settings.android.compose")
}

android {
    namespace = "com.android.car.settings.core.ui"
}

dependencies {
    implementation(project(":core:vehicle"))
    implementation(libs.androidx.core.ktx)

    // B-Material is the design-system source of truth for the automotive UI. Keep this list
    // deliberately focused on components consumed by the shared UI layer.
    implementation(libs.bmaterial.ui.core.tokens)
    implementation(libs.bmaterial.ui.core.resources)
    implementation(libs.bmaterial.ui.core.foundation)
    implementation(libs.bmaterial.ui.components.button)
    implementation(libs.bmaterial.ui.components.dialog)
    implementation(libs.bmaterial.ui.components.card)
    implementation(libs.bmaterial.ui.components.chip)
    implementation(libs.bmaterial.ui.components.indicator)
    implementation(libs.bmaterial.ui.components.layout)
    implementation(libs.bmaterial.ui.components.listitem)
    implementation(libs.bmaterial.ui.components.loading)
    implementation(libs.bmaterial.ui.components.slider)
    implementation(libs.bmaterial.ui.components.snackbar)
    implementation(libs.bmaterial.ui.components.scrollbar)
    implementation(libs.bmaterial.ui.components.switch)
    implementation(libs.bmaterial.ui.components.text)
    implementation(libs.bmaterial.ui.components.textfield)
    implementation(libs.bmaterial.ccp.rotary.focus)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
