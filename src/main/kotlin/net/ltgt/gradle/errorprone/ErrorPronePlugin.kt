package net.ltgt.gradle.errorprone

import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestExtension
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.TestVariant
import com.android.build.gradle.api.UnitTestVariant
import org.gradle.api.JavaVersion
import org.gradle.api.Named
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.* // ktlint-disable no-wildcard-imports
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.util.GradleVersion

/**
 * A [Plugin] that configures [JavaCompile] tasks to use the [Error Prone compiler](https://errorprone.info/).
 */
class ErrorPronePlugin : Plugin<Project> {

    companion object {
        const val PLUGIN_ID = "net.ltgt.errorprone"

        const val CONFIGURATION_NAME = "errorprone"

        const val JAVAC_CONFIGURATION_NAME = "errorproneJavac"

        internal const val TOO_OLD_TOOLCHAIN_ERROR_MESSAGE = "Must not enable ErrorProne when compiling with JDK < 8"

        internal val JVM_ARGS_STRONG_ENCAPSULATION = listOf(
            "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED"
        )
    }

    override fun apply(project: Project) {
        if (GradleVersion.current() < GradleVersion.version("6.8")) {
            throw UnsupportedOperationException("$PLUGIN_ID requires at least Gradle 6.8")
        }

        val errorproneConfiguration = project.configurations.create(CONFIGURATION_NAME) {
            description = "Error Prone dependencies, will be extended by all source sets' annotationProcessor configurations"
            isVisible = false
            isCanBeConsumed = false
            // This configuration is not meant to be resolved, but there's no reason it couldn't be (and there's a test that does this)
            isCanBeResolved = true

            exclude(group = "com.google.errorprone", module = "javac")
        }
        val javacConfiguration: FileCollection = project.configurations.create(JAVAC_CONFIGURATION_NAME) {
            description = "Error Prone Javac dependencies, will only be used when using JDK 8 (i.e. not JDK 9 or superior)"
            isVisible = false
            isCanBeConsumed = false
            isCanBeResolved = true
            defaultDependencies {
                add(project.dependencies.create("com.google.errorprone:javac:9+181-r4173-1"))
            }
        }

        fun JavaCompile.configureErrorProneJavac() {
            options.forkOptions.jvmArgs!!.add("-Xbootclasspath/p:${javacConfiguration.asPath}")
        }

        val providers = project.providers
        project.tasks.withType<JavaCompile>().configureEach {
            val errorproneOptions =
                (options as ExtensionAware).extensions.create(ErrorProneOptions.NAME, ErrorProneOptions::class.java)
            options
                .compilerArgumentProviders
                .add(ErrorProneCompilerArgumentProvider(errorproneOptions))

            inputs.files(
                providers.provider {
                    when {
                        !errorproneOptions.isEnabled.getOrElse(false) -> emptyList()
                        compilerVersion == JavaVersion.VERSION_1_8 -> javacConfiguration
                        else -> emptyList()
                    }
                }
            ).withPropertyName(JAVAC_CONFIGURATION_NAME).withNormalizer(ClasspathNormalizer::class)
            doFirst("configure JVM arguments and forking for errorprone") {
                if (!errorproneOptions.isEnabled.getOrElse(false)) return@doFirst
                compilerVersion?.let {
                    if (it < JavaVersion.VERSION_1_8) throw UnsupportedOperationException(TOO_OLD_TOOLCHAIN_ERROR_MESSAGE)
                    if (it.needsForking) options.isFork = true
                    if (it == JavaVersion.VERSION_1_8) {
                        configureErrorProneJavac()
                    } else {
                        configureForJava9plus()
                    }
                }
            }
        }

        val enableErrorProne: JavaCompile.(Boolean) -> Unit = { testOnly ->
            options.errorprone {
                isEnabled.convention(javaCompiler.map { it.metadata.languageVersion.asInt() >= 8 }.orElse(true))
                isCompilingTestOnlyCode.convention(testOnly)
            }
        }

        project.plugins.withType<JavaBasePlugin> {
            project.extensions.getByName<SourceSetContainer>("sourceSets").configureEach {
                project.configurations[annotationProcessorConfigurationName].extendsFrom(errorproneConfiguration)
                project.tasks.named<JavaCompile>(compileJavaTaskName) {
                    enableErrorProne(this@configureEach.name.matches(TEST_SOURCE_SET_NAME_REGEX))
                }
            }
        }

        arrayOf("application", "library", "test", "dynamic-feature").forEach {
            project.plugins.withId("com.android.$it") {
                fun BaseVariant.configure() {
                    annotationProcessorConfiguration.extendsFrom(errorproneConfiguration)
                    javaCompileProvider.configure {
                        enableErrorProne(this@configure is TestVariant || this@configure is UnitTestVariant)
                    }
                }

                val android = project.extensions.getByName<BaseExtension>("android")
                (android as? AppExtension)?.applicationVariants?.configureEach(BaseVariant::configure)
                (android as? LibraryExtension)?.libraryVariants?.configureEach(BaseVariant::configure)
                (android as? TestExtension)?.applicationVariants?.configureEach(BaseVariant::configure)
                if (android is TestedExtension) {
                    android.testVariants.configureEach(BaseVariant::configure)
                    android.unitTestVariants.configureEach(BaseVariant::configure)
                }
            }
        }
    }

    private val JavaCompile.compilerVersion get() =
        javaCompiler
            .map { JavaVersion.toVersion(it.metadata.languageVersion.asInt()) }
            .orNull ?: if (options.isCommandLine) null else JavaVersion.current()

    private val JavaVersion.needsForking get() =
        this == JavaVersion.VERSION_1_8 || this >= JavaVersion.VERSION_16

    private fun JavaCompile.configureForJava9plus() {
        // https://errorprone.info/docs/installation#java-9-and-newer
        options.forkOptions.jvmArgs!!.addAll(JVM_ARGS_STRONG_ENCAPSULATION)
    }
}

internal class ErrorProneCompilerArgumentProvider(
    private val errorproneOptions: ErrorProneOptions
) : CommandLineArgumentProvider, Named {

    @Internal override fun getName(): String = "errorprone"

    @Suppress("unused")
    @Nested
    @Optional
    fun getErrorproneOptions(): ErrorProneOptions? {
        return errorproneOptions.takeIf { it.isEnabled.getOrElse(false) }
    }

    override fun asArguments(): Iterable<String> {
        return when {
            errorproneOptions.isEnabled.getOrElse(false) -> listOf("-Xplugin:ErrorProne $errorproneOptions", "-XDcompilePolicy=simple")
            else -> emptyList()
        }
    }
}

internal val TEST_SOURCE_SET_NAME_REGEX =
    """^(t|.*T)est(\p{javaUpperCase}.*)?$""".toRegex()

private val CompileOptions.isCommandLine
    get() = isFork && (forkOptions.javaHome != null || forkOptions.executable != null)
