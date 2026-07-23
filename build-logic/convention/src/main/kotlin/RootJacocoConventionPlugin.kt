import org.gradle.api.Plugin
import org.gradle.api.Project

class RootJacocoConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        check(this == rootProject) {
            "launcher.root.jacoco must be applied to the root project only."
        }

        val jacocoTestReport = tasks.register("jacocoTestReport") {
            group = "verification"
            description = "Runs JaCoCo reports across all Android modules."
            dependsOn(subprojects.map { "${it.path}:jacocoTestReport" })
        }
    }
}
