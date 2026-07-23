import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("settings.android.ktlint")
        pluginManager.apply("settings.android.detekt")
        pluginManager.apply("settings.android.jacoco")

        extensions.configure<ApplicationExtension> {
            compileSdk = 36

            defaultConfig {
                minSdk = 26
                targetSdk = 36
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                vectorDrawables.useSupportLibrary = true
            }

            buildFeatures {
                viewBinding = true
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }

            packaging {
                resources {
                    excludes += "/META-INF/{AL2.0,LGPL2.1}"
                }
            }
        }
    }
}
