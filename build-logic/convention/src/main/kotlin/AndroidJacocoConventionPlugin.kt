import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

class AndroidJacocoConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("jacoco")
        configureJacocoTooling()

        tasks.withType(Test::class.java).configureEach {
            extensions.configure(JacocoTaskExtension::class.java) {
                isIncludeNoLocationClasses = true
                excludes = listOf("jdk.internal.*")
            }
        }

        plugins.withId("com.android.application") {
            registerAndroidCoverageTask()
        }
        plugins.withId("com.android.library") {
            registerAndroidCoverageTask()
        }
    }

    private fun Project.configureJacocoTooling() {
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        val jacocoVersion = libs.findVersion("jacoco").get().requiredVersion

        extensions.configure(JacocoPluginExtension::class.java) {
            toolVersion = jacocoVersion
        }
    }

    private fun Project.registerAndroidCoverageTask() {
        val coverageExcludes = listOf(
            "**/R.class",
            "**/R\$*.class",
            "**/BuildConfig.*",
            "**/Manifest*.*",
            "**/*Test*.*",
            "**/*\$ViewInjector*.*",
            "**/*\$ViewBinder*.*",
            "**/*BR*.*",
            "**/databinding/*",
            "**/android/databinding/*",
            "**/*MapperImpl*.*",
            "**/hilt_aggregated_deps/**",
            "**/*_Factory*.*",
            "**/*_MembersInjector*.*",
            "**/*_HiltModules*.*",
            "**/*_GeneratedInjector*.*",
            "**/*_Impl*.*",
        )

        val reportTask = tasks.register<JacocoReport>("jacocoTestReport") {
            group = "verification"
            description = "Generates JaCoCo coverage reports for debug unit tests."
            dependsOn("testDebugUnitTest")

            reports {
                xml.required.set(true)
                html.required.set(true)
                csv.required.set(false)
            }

            val debugJavaClasses = fileTree(
                layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes")
                    .get()
                    .asFile,
            ) {
                exclude(coverageExcludes)
            }
            val debugKotlinClasses = fileTree(
                layout.buildDirectory.dir("tmp/kotlin-classes/debug").get().asFile,
            ) {
                exclude(coverageExcludes)
            }

            classDirectories.setFrom(files(debugJavaClasses, debugKotlinClasses))
            sourceDirectories.setFrom(
                files(
                    "src/main/java",
                    "src/main/kotlin",
                ),
            )
            executionData.setFrom(
                fileTree(layout.buildDirectory.asFile.get()) {
                    include("jacoco/testDebugUnitTest.exec")
                    include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
                },
            )
        }
    }
}
