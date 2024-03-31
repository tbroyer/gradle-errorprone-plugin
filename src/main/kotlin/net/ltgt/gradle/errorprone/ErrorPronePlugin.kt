package net.ltgt.gradle.errorprone

import org.gradle.api.JavaVersion
import org.gradle.api.Named
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.* // ktlint-disable no-wildcard-imports
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.util.GradleVersion
import javax.inject.Inject

/**
 * A [Plugin] that configures [JavaCompile] tasks to use the [Error Prone compiler](https://errorprone.info/).
 */
class ErrorPronePlugin @Inject constructor(
    private val providers: ProviderFactory,
) : Plugin<Project> {

    companion object {
        const val PLUGIN_ID = "net.ltgt.errorprone"

        const val CONFIGURATION_NAME = "errorprone"

        const val JAVAC_CONFIGURATION_NAME = "errorproneJavac"

        internal const val TOO_OLD_TOOLCHAIN_ERROR_MESSAGE = "Must not enable ErrorProne when compiling with JDK < 8"

        private val HAS_JVM_ARGUMENT_PROVIDERS = GradleVersion.current() >= GradleVersion.version("7.1")

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
            "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
        )

        private val CURRENT_JVM_NEEDS_FORKING by lazy {
            // Needs bootclasspath
            JavaVersion.current() == JavaVersion.VERSION_1_8 || (
                // Needs --add-exports and --add-opens
                JavaVersion.current() >= JavaVersion.VERSION_16 &&
                    try {
                        sequenceOf(
                            "com.sun.tools.javac.api.BasicJavacTask",
                            "com.sun.tools.javac.api.JavacTrees",
                            "com.sun.tools.javac.file.JavacFileManager",
                            "com.sun.tools.javac.main.JavaCompiler",
                            "com.sun.tools.javac.model.JavacElements",
                            "com.sun.tools.javac.parser.JavacParser",
                            "com.sun.tools.javac.processing.JavacProcessingEnvironment",
                            "com.sun.tools.javac.tree.JCTree",
                            "com.sun.tools.javac.util.JCDiagnostic",
                        ).any {
                            val klass = Class.forName(it)
                            return@any !klass.module.isExported(klass.packageName, this::class.java.classLoader.unnamedModule)
                        } &&
                            sequenceOf(
                                "com.sun.tools.javac.code.Symbol",
                                "com.sun.tools.javac.comp.Enter",
                            ).any {
                                val klass = Class.forName(it)
                                return@any !klass.module.isOpen(klass.packageName, this::class.java.classLoader.unnamedModule)
                            }
                    } catch (e: ClassNotFoundException) {
                        true
                    }
                )
        }
    }

    override fun apply(project: Project) {
        if (GradleVersion.current() < GradleVersion.version("6.8")) {
            throw UnsupportedOperationException("$PLUGIN_ID requires at least Gradle 6.8")
        }

        val errorproneConfiguration = project.configurations.create(CONFIGURATION_NAME) {
            description = "Error Prone dependencies, will be extended by all source sets' annotationProcessor configurations"
            isVisible = false
            isCanBeConsumed = false
            isCanBeResolved = false

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

        project.tasks.withType<JavaCompile>().configureEach {
            val errorproneOptions =
                (options as ExtensionAware).extensions.create(ErrorProneOptions.NAME, ErrorProneOptions::class.java)
            options
                .compilerArgumentProviders
                .add(ErrorProneCompilerArgumentProvider(errorproneOptions))

            val jvmArgumentProvider = ErrorProneJvmArgumentProvider(this, errorproneOptions, javacConfiguration)
            if (HAS_JVM_ARGUMENT_PROVIDERS) {
                options.forkOptions.jvmArgumentProviders.add(jvmArgumentProvider)
            } else {
                inputs.property("errorprone.compilerVersion", providers.provider { jvmArgumentProvider.compilerVersion })
                    .optional(true)
                inputs.files(providers.provider { jvmArgumentProvider.bootstrapClasspath })
                    .withPropertyName("errorprone.bootstrapClasspath")
                    .withNormalizer(ClasspathNormalizer::class)
                    .optional()
                doFirst("Configure JVM arguments for errorprone") {
                    options.forkOptions.jvmArgs!!.addAll(jvmArgumentProvider.asArguments())
                }
            }
            doFirst("Configure forking for errorprone") {
                if (!errorproneOptions.isEnabled.getOrElse(false)) return@doFirst
                jvmArgumentProvider.compilerVersion?.let {
                    if (it < JavaVersion.VERSION_1_8) throw UnsupportedOperationException(TOO_OLD_TOOLCHAIN_ERROR_MESSAGE)
                    if (it == JavaVersion.VERSION_1_8 || (it == JavaVersion.current() && CURRENT_JVM_NEEDS_FORKING)) options.isFork = true
                }
            }
        }

        project.plugins.withType<JavaBasePlugin> {
            project.extensions.getByName<SourceSetContainer>("sourceSets").configureEach {
                project.configurations[annotationProcessorConfigurationName].extendsFrom(errorproneConfiguration)
                project.tasks.named<JavaCompile>(compileJavaTaskName) {
                    options.errorprone {
                        isEnabled.convention(javaCompiler.map { it.metadata.languageVersion.asInt() >= 8 }.orElse(true))
                        isCompilingTestOnlyCode.convention(this@configureEach.name.matches(TEST_SOURCE_SET_NAME_REGEX))
                    }
                }
            }
        }
    }
}

internal class ErrorProneJvmArgumentProvider(
    private val task: JavaCompile,
    private val errorproneOptions: ErrorProneOptions,
    private val javacConfiguration: FileCollection,
) : CommandLineArgumentProvider, Named {

    @Internal override fun getName(): String = "errorprone"

    @get:Input
    @get:Optional
    val compilerVersion by lazy {
        task.javaCompiler
            .map { JavaVersion.toVersion(it.metadata.languageVersion.asInt()) }
            .orNull ?: if (task.options.isCommandLine) null else JavaVersion.current()
    }

    @get:Classpath
    @get:Optional
    val bootstrapClasspath get() = javacConfiguration.takeIf {
        errorproneOptions.isEnabled.getOrElse(false) &&
            compilerVersion == JavaVersion.VERSION_1_8
    }

    override fun asArguments(): Iterable<String> = when {
        !errorproneOptions.isEnabled.getOrElse(false) -> emptyList()
        compilerVersion == null -> emptyList()
        compilerVersion == JavaVersion.VERSION_1_8 -> listOf("-Xbootclasspath/p:${javacConfiguration.asPath}")
        compilerVersion!! > JavaVersion.VERSION_1_8 -> ErrorPronePlugin.JVM_ARGS_STRONG_ENCAPSULATION
        else -> emptyList()
    }
}

internal class ErrorProneCompilerArgumentProvider(
    private val errorproneOptions: ErrorProneOptions,
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
