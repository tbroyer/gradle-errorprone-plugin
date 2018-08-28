package net.ltgt.gradle.errorprone.javacplugin

import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.FeatureExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestExtension
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.api.BaseVariant
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Named
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.* // ktlint-disable no-wildcard-imports
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.util.GradleVersion

class ErrorProneJavacPluginPlugin : Plugin<Project> {

    companion object {
        const val PLUGIN_ID = "net.ltgt.errorprone-javacplugin"

        const val CONFIGURATION_NAME = "errorprone"

        private val MIN_GRADLE_VERSION_WITH_LAZY_TASKS = GradleVersion.version("4.9")

        internal fun supportsLazyTasks(version: GradleVersion) = version >= MIN_GRADLE_VERSION_WITH_LAZY_TASKS

        private val SUPPORTS_LAZY_TASKS = supportsLazyTasks(GradleVersion.current())
    }

    override fun apply(project: Project) {
        if (GradleVersion.current() < GradleVersion.version("4.6")) {
            throw UnsupportedOperationException("$PLUGIN_ID requires at least Gradle 4.6")
        }

        val errorproneConfiguration = project.configurations.create(CONFIGURATION_NAME) {
            isVisible = false
            exclude(group = "com.google.errorprone", module = "javac")
        }

        project.tasks.withType<JavaCompile>().configureElement {
            val errorproneOptions =
                (options as ExtensionAware).extensions.create(ErrorProneOptions.NAME, ErrorProneOptions::class.java)
            options
                .compilerArgumentProviders
                .add(ErrorProneCompilerArgumentProvider(errorproneOptions))
        }

        project.plugins.withType<JavaBasePlugin> {
            // XXX: move to project.extensions.getByName<SourceSetContainer>("sourceSets") when changing min version to 4.10+
            val java = project.convention.getPlugin<JavaPluginConvention>()
            java.sourceSets.configureElement {
                project.configurations[annotationProcessorConfigurationName].extendsFrom(errorproneConfiguration)
                project.configureTask<JavaCompile>(compileJavaTaskName) {
                    options.errorprone.isEnabled = true
                }
            }
        }

        project.plugins.withType<JavaPlugin> {
            project.configureTask<JavaCompile>(JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME) {
                options.errorprone.isCompilingTestOnlyCode = true
            }
        }

        arrayOf("application", "library", "feature", "test", "instantapp").forEach {
            project.plugins.withId("com.android.$it") {
                val configure: BaseVariant.() -> Unit = {
                    annotationProcessorConfiguration.extendsFrom(errorproneConfiguration)
                    (javaCompiler as? JavaCompile)?.options?.errorprone?.isEnabled = true
                }

                val android = project.extensions.getByName<BaseExtension>("android")
                (android as? AppExtension)?.applicationVariants?.configureElement(configure)
                (android as? LibraryExtension)?.libraryVariants?.configureElement(configure)
                (android as? FeatureExtension)?.featureVariants?.configureElement(configure)
                (android as? TestExtension)?.applicationVariants?.configureElement(configure)
                if (android is TestedExtension) {
                    android.testVariants.configureElement(configure)
                    android.unitTestVariants.configureElement(configure)
                }
            }
        }
    }

    private inline fun <reified T : Task> Project.configureTask(taskName: String, noinline action: T.() -> Unit) {
        if (SUPPORTS_LAZY_TASKS) {
            tasks.withType(T::class.java).named(taskName).configure(action)
        } else {
            tasks.withType(T::class.java).getByName(taskName, action)
        }
    }

    private fun <T> DomainObjectCollection<out T>.configureElement(action: T.() -> Unit) {
        if (SUPPORTS_LAZY_TASKS) {
            this.configureEach(action)
        } else {
            this.all(action)
        }
    }
}

internal class ErrorProneCompilerArgumentProvider(private val errorproneOptions: ErrorProneOptions) :
    CommandLineArgumentProvider, Named {

    override fun getName(): String = "errorprone"

    @Suppress("unused")
    @Nested
    @Optional
    fun getErrorproneOptions(): ErrorProneOptions? {
        return errorproneOptions.takeIf { it.isEnabled }
    }

    override fun asArguments(): Iterable<String> {
        return when {
            errorproneOptions.isEnabled -> listOf("-Xplugin:ErrorProne $errorproneOptions", "-XDcompilePolicy=simple")
            else -> emptyList()
        }
    }
}
