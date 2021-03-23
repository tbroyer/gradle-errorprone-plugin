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
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.ForkOptions
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.* // ktlint-disable no-wildcard-imports
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.util.GradleVersion
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A [Plugin] that configures [JavaCompile] tasks to use the [Error Prone compiler](https://errorprone.info/).
 */
class ErrorPronePlugin : Plugin<Project> {

    companion object {
        const val PLUGIN_ID = "net.ltgt.errorprone"

        const val CONFIGURATION_NAME = "errorprone"

        const val JAVAC_CONFIGURATION_NAME = "errorproneJavac"

        private val LOGGER = Logging.getLogger(ErrorPronePlugin::class.java)

        internal const val NO_JAVAC_DEPENDENCY_WARNING_MESSAGE =
"""No dependency was configured in configuration $JAVAC_CONFIGURATION_NAME, compilation with Error Prone will likely fail as a result.
Add a dependency to com.google.errorprone:javac with the appropriate version corresponding to the version of Error Prone you're using:

    dependencies {
        $JAVAC_CONFIGURATION_NAME("com.google.errorprone:javac:${'$'}errorproneJavacVersion")
    }
"""

        private val HAS_TOOLCHAINS = GradleVersion.current().baseVersion >= GradleVersion.version("6.7")

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
        if (GradleVersion.current() < GradleVersion.version("5.2")) {
            throw UnsupportedOperationException("$PLUGIN_ID requires at least Gradle 5.2")
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
        }

        val noJavacDependencyNotified = AtomicBoolean()
        fun JavaCompile.configureErrorProneJavac() {
            if (!options.isFork) {
                options.isFork = true
                // reset forkOptions in case they were configured
                options.forkOptions = ForkOptions()
            }
            javacConfiguration.asPath.also {
                if (it.isNotBlank()) {
                    options.forkOptions.jvmArgs!!.add("-Xbootclasspath/p:$it")
                } else if (noJavacDependencyNotified.compareAndSet(false, true)) {
                    LOGGER.warn(NO_JAVAC_DEPENDENCY_WARNING_MESSAGE)
                }
            }
        }

        val providers = project.providers
        project.tasks.withType<JavaCompile>().configureEach {
            val errorproneOptions =
                (options as ExtensionAware).extensions.create(ErrorProneOptions.NAME, ErrorProneOptions::class.java)
            options
                .compilerArgumentProviders
                .add(ErrorProneCompilerArgumentProvider(errorproneOptions))

            if (HAS_TOOLCHAINS || JavaVersion.current().run { isJava8 || isJava16Compatible }) {
                inputs.files(
                    providers.provider {
                        when {
                            !options.errorprone.isEnabled.getOrElse(false) -> emptyList()
                            HAS_TOOLCHAINS && javaCompiler.isPresent -> {
                                when (javaCompiler.get().metadata.languageVersion.asInt()) {
                                    8 -> javacConfiguration
                                    else -> emptyList()
                                }
                            }
                            JavaVersion.current().isJava8 -> javacConfiguration
                            else -> emptyList()
                        }
                    }
                ).withPropertyName(JAVAC_CONFIGURATION_NAME).withNormalizer(ClasspathNormalizer::class)
                doFirst("configure errorprone in bootclasspath") {
                    when {
                        !options.errorprone.isEnabled.getOrElse(false) -> return@doFirst
                        HAS_TOOLCHAINS && javaCompiler.isPresent -> {
                            val targetVersion = javaCompiler.get().metadata.languageVersion.asInt()
                            when {
                                targetVersion < 8 -> throw UnsupportedOperationException(TOO_OLD_TOOLCHAIN_ERROR_MESSAGE)
                                targetVersion == 8 -> configureErrorProneJavac()
                                targetVersion >= 16 -> configureForJava16plus()
                            }
                        }
                        JavaVersion.current().isJava8 && (!options.isFork || (options.forkOptions.javaHome == null && options.forkOptions.executable == null)) ->
                            configureErrorProneJavac()
                        JavaVersion.current().isJava16Compatible && (!options.isFork || (options.forkOptions.javaHome == null && options.forkOptions.executable == null)) ->
                            configureForJava16plus()
                    }
                }
            }
        }

        val enableErrorProne: JavaCompile.(Boolean) -> Unit = { testOnly ->
            options.errorprone {
                if (HAS_TOOLCHAINS) {
                    isEnabled.convention(javaCompiler.map { it.metadata.languageVersion.asInt() >= 8 }.orElse(true))
                } else {
                    isEnabled.convention(true)
                }
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

    private fun JavaCompile.configureForJava16plus() {
        // https://github.com/google/error-prone/issues/1157#issuecomment-769289564
        if (!options.isFork) {
            options.isFork = true
            // reset forkOptions in case they were configured
            options.forkOptions = ForkOptions()
        }
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

internal val JavaVersion.isJava16Compatible: Boolean
    get() = this < JavaVersion.VERSION_HIGHER && this >= JavaVersion.toVersion(16)
