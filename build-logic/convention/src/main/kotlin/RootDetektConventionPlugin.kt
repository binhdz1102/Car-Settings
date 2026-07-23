import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

class RootDetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        check(this == rootProject) {
            "launcher.root.detekt must be applied to the root project only."
        }

        val detekt = tasks.register("detekt") {
            group = "verification"
            description = "Runs detekt across all Android modules."
        }

        val detektBaseline = tasks.register("detektBaseline") {
            group = "verification"
            description = "Generates detekt baselines across all Android modules."
        }

        subprojects {
            wireAggregateTask(
                aggregateTask = detekt,
                pluginId = "dev.detekt",
                taskName = "detekt",
            )
            wireAggregateTask(
                aggregateTask = detektBaseline,
                pluginId = "dev.detekt",
                taskName = "detektBaseline",
            )
        }
    }

    private fun Project.wireAggregateTask(
        aggregateTask: TaskProvider<org.gradle.api.Task>,
        pluginId: String,
        taskName: String,
    ) {
        pluginManager.withPlugin(pluginId) {
            aggregateTask.configure {
                dependsOn(tasks.named(taskName))
            }
        }
    }
}
