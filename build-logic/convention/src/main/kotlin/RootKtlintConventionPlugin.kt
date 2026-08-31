import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

class RootKtlintConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        check(this == rootProject) {
            "launcher.root.ktlint must be applied to the root project only."
        }

        val ktlintCheck = tasks.register("ktlintCheck") {
            group = "verification"
            description = "Runs ktlint checks across all Android modules."
        }

        val ktlintFormat = tasks.register("ktlintFormat") {
            group = "formatting"
            description = "Formats Kotlin sources with ktlint across all Android modules."
        }

        subprojects {
            wireAggregateTask(aggregateTask = ktlintCheck, taskName = "ktlintCheck")
            wireAggregateTask(aggregateTask = ktlintFormat, taskName = "ktlintFormat")
        }
    }

    private fun Project.wireAggregateTask(
        aggregateTask: TaskProvider<org.gradle.api.Task>,
        taskName: String,
    ) {
        tasks.matching { it.name == taskName }.configureEach {
            aggregateTask.configure {
                dependsOn(this@configureEach)
            }
        }
    }
}
