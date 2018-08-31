package net.ltgt.gradle.errorprone

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.plugin
import java.util.concurrent.atomic.AtomicBoolean

internal fun deprecationMessage(pluginId: String) =
    "Plugin $pluginId is deprecated, please migrate to ${ErrorPronePlugin.PLUGIN_ID}"

@Deprecated("Will be removed in the next version")
class ErrorProneBasePlugin : Plugin<Project> {
    companion object {
        internal const val PLUGIN_ID = "net.ltgt.errorprone-base"

        private val notified = AtomicBoolean()
    }

    override fun apply(project: Project) {
        if (notified.compareAndSet(false, true)) {
            project.logger.warn(deprecationMessage(PLUGIN_ID))
        }
    }
}

@Deprecated("Will be removed in the next version")
class ErrorProneJavacPluginPlugin : Plugin<Project> {
    companion object {
        internal const val PLUGIN_ID = "net.ltgt.errorprone-javacplugin"

        private val notified = AtomicBoolean()
    }

    override fun apply(project: Project) {
        if (notified.compareAndSet(false, true)) {
            project.logger.warn(deprecationMessage(PLUGIN_ID))
        }

        project.apply(Action {
            plugin(ErrorPronePlugin::class)
        })
    }
}
