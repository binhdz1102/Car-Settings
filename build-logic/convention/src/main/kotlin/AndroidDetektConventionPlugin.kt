import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType

class AndroidDetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("dev.detekt")

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        val detektVersion = libs.findVersion("detekt").get().requiredVersion
        val sharedConfig = rootProject.layout.projectDirectory.file("config/detekt/detekt.yml")
        val moduleBaseline = layout.projectDirectory.file("detekt-baseline.xml")

        extensions.configure<DetektExtension> {
            toolVersion.set(detektVersion)
            buildUponDefaultConfig.set(true)
            allRules.set(false)
            parallel.set(true)
            ignoreFailures.set(false)
            autoCorrect.set(false)
            basePath.set(rootProject.layout.projectDirectory)

            if (sharedConfig.asFile.exists()) {
                config.setFrom(sharedConfig)
            }
            if (moduleBaseline.asFile.exists()) {
                baseline.set(moduleBaseline)
            }
        }
    }
}
