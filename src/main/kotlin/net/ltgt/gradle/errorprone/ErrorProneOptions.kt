package net.ltgt.gradle.errorprone

import org.gradle.api.InvalidUserDataException
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.kotlin.dsl.* // ktlint-disable no-wildcard-imports
import org.gradle.process.CommandLineArgumentProvider

open class ErrorProneOptions {
    @get:Input var isEnabled: Boolean = false
    @get:Input var disableAllChecks: Boolean = false
    @get:Input var allErrorsAsWarnings: Boolean = false
    @get:Input var allDisabledChecksAsWarnings: Boolean = false
    @get:Input var disableWarningsInGeneratedCode: Boolean = false
    @get:Input var ignoreUnknownCheckNames: Boolean = false
    @get:Input var ignoreSuppressionAnnotations: Boolean = false
    @get:Input var isCompilingTestOnlyCode: Boolean = false
    @get:Input @get:Optional var excludedPaths: String? = null
    @get:Input var checks: MutableMap<String, CheckSeverity> = linkedMapOf()
    @get:Input var checkOptions: MutableMap<String, String> = linkedMapOf()
    @get:Input var errorproneArgs: MutableList<String> = arrayListOf()
    @get:Nested val errorproneArgumentProviders: MutableList<CommandLineArgumentProvider> = arrayListOf()

    companion object {
        const val NAME = "errorprone"

        private fun validate(arg: String) {
            if (arg.contains("""\p{IsWhite_Space}""".toRegex()))
                throw InvalidUserDataException("""Error Prone options cannot contain white space: "$arg".""")
        }

        private fun validateName(arg: Map.Entry<String, Any>) {
            if (arg.key.contains(':'))
                throw InvalidUserDataException("""Error Prone check name cannot contain a colon (":"): "${arg.key}".""")
        }
    }

    fun check(vararg checkNames: String) = checks.putAll(checkNames.map { it to CheckSeverity.DEFAULT })
    fun check(vararg pairs: Pair<String, CheckSeverity>) = checks.putAll(pairs)
    fun check(checkName: String, severity: CheckSeverity) {
        checks[checkName] = severity
    }

    fun option(name: String) = option(name, "true")
    fun option(name: String, value: String) {
        checkOptions[name] = value
    }

    override fun toString(): String {
        return (
            sequenceOf(
                "-XepDisableAllChecks".takeIf { disableAllChecks },
                "-XepAllErrorsAsWarnings".takeIf { allErrorsAsWarnings },
                "-XepAllDisabledChecksAsWarnings".takeIf { allDisabledChecksAsWarnings },
                "-XepDisableWarningsInGeneratedCode".takeIf { disableWarningsInGeneratedCode },
                "-XepIgnoreUnknownCheckNames".takeIf { ignoreUnknownCheckNames },
                "-XepIgnoreSuppressionAnnotations".takeIf { ignoreSuppressionAnnotations },
                "-XepCompilingTestOnlyCode".takeIf { isCompilingTestOnlyCode },
                "-XepExcludedPaths:$excludedPaths".takeUnless { excludedPaths.isNullOrEmpty() }
            ).filterNotNull() +
                checks.asSequence().onEach(::validateName).map { (name, severity) -> "-Xep:$name${severity.asArg}" } +
                checkOptions.asSequence().map { (name, value) -> "-XepOpt:$name=$value" } +
                errorproneArgs +
                errorproneArgumentProviders.asSequence().flatMap { it.asArguments().asSequence() }
            ).onEach(::validate)
            .joinToString(separator = " ")
    }
}

enum class CheckSeverity {
    DEFAULT, OFF, WARN, ERROR;

    internal val asArg: String
        get() = if (this == DEFAULT) "" else ":$name"
}

// Extensions
val CompileOptions.errorprone: ErrorProneOptions
    get() = (this as ExtensionAware).extensions.getByName<ErrorProneOptions>(ErrorProneOptions.NAME)

operator fun ErrorProneOptions.invoke(configure: ErrorProneOptions.() -> Unit): ErrorProneOptions =
    apply(configure)
