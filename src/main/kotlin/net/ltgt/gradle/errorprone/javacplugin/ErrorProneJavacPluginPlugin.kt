package net.ltgt.gradle.errorprone.javacplugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.* // ktlint-disable no-wildcard-imports
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.util.GradleVersion

class ErrorProneJavacPluginPlugin : Plugin<Project> {

    companion object {
        const val PLUGIN_ID = "net.ltgt.errorprone-javacplugin"

        const val CONFIGURATION_NAME = "errorprone"

        const val DEFAULT_DEPENDENCY = "com.google.errorprone:error_prone_core:latest.release"
    }

    override fun apply(project: Project) {
        if (GradleVersion.current() < GradleVersion.version("4.6")) {
            throw UnsupportedOperationException("$PLUGIN_ID requires at least Gradle 4.6")
        }

        val errorproneConfiguration = project.configurations.create(CONFIGURATION_NAME) {
            isVisible = false
            defaultDependencies { add(project.dependencies.create(DEFAULT_DEPENDENCY)) }
        }

        project.plugins.withType<JavaBasePlugin> {
            val java = project.convention.getPlugin<JavaPluginConvention>()
            java.sourceSets.all {
                project.configurations[annotationProcessorConfigurationName].extendsFrom(errorproneConfiguration)
                ErrorProneJavacPlugin.apply((project.tasks[compileJavaTaskName] as JavaCompile).options)
            }
        }
    }
}

object ErrorProneJavacPlugin {
    @JvmStatic
    fun apply(options: CompileOptions) {
        val errorproneOptions = DslObject(options)
            .extensions
            .create(ErrorProneOptions.NAME, ErrorProneOptions::class.java)
        options
            .compilerArgumentProviders
            .add(ErrorProneCompilerArgumentProvider(errorproneOptions))
    }
}

internal class ErrorProneCompilerArgumentProvider(private val errorproneOptions: ErrorProneOptions) :
    CommandLineArgumentProvider {

    @Nested
    @Optional
    fun getErrorproneOptions(): ErrorProneOptions? {
        return errorproneOptions.takeIf { it.isEnabled }
    }

    override fun asArguments(): Iterable<String> {
        return when {
            errorproneOptions.isEnabled -> listOf(errorproneOptions.toString())
            else -> emptyList()
        }
    }
}
