package net.ltgt.gradle.errorprone

import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.kotlin.dsl.* // ktlint-disable no-wildcard-imports
import org.gradle.process.CommandLineArgumentProvider

open class ErrorProneOptions constructor(
    objectFactory: ObjectFactory
) {
    @get:Input val isEnabled = objectFactory.property<Boolean>().byConvention(false)
    @get:Input val disableAllChecks = objectFactory.property<Boolean>().byConvention(false)
    @get:Input val allErrorsAsWarnings = objectFactory.property<Boolean>().byConvention(false)
    @get:Input val allDisabledChecksAsWarnings = objectFactory.property<Boolean>().byConvention(false)
    @get:Input val disableWarningsInGeneratedCode = objectFactory.property<Boolean>().byConvention(false)
    @get:Input val ignoreUnknownCheckNames = objectFactory.property<Boolean>().byConvention(false)
    @get:Input val ignoreSuppressionAnnotations = objectFactory.property<Boolean>().byConvention(false)
    @get:Input val isCompilingTestOnlyCode = objectFactory.property<Boolean>().byConvention(false)
    @get:Input @get:Optional val excludedPaths = objectFactory.property<String>()
    @get:Input var checks: MutableMap<String, CheckSeverity> = linkedMapOf()
    @get:Input var checkOptions: MutableMap<String, String> = linkedMapOf()
    @get:Input val errorproneArgs = objectFactory.listProperty<String>().setEmpty()
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
                booleanOption("-XepDisableAllChecks", disableAllChecks),
                booleanOption("-XepAllErrorsAsWarnings", allErrorsAsWarnings),
                booleanOption("-XepAllDisabledChecksAsWarnings", allDisabledChecksAsWarnings),
                booleanOption("-XepDisableWarningsInGeneratedCode", disableWarningsInGeneratedCode),
                booleanOption("-XepIgnoreUnknownCheckNames", ignoreUnknownCheckNames),
                booleanOption("-XepIgnoreSuppressionAnnotations", ignoreSuppressionAnnotations),
                booleanOption("-XepCompilingTestOnlyCode", isCompilingTestOnlyCode),
                stringOption("-XepExcludedPaths", excludedPaths)
            ).filterNotNull() +
                checks.asSequence().onEach(::validateName).map { (name, severity) -> "-Xep:$name${severity.asArg}" } +
                checkOptions.asSequence().map { (name, value) -> "-XepOpt:$name=$value" } +
                errorproneArgs.getOrElse(emptyList()) +
                errorproneArgumentProviders.asSequence().flatMap { it.asArguments().asSequence() }
            ).onEach(::validate)
            .joinToString(separator = " ")
    }

    private fun booleanOption(name: String, value: Provider<Boolean>): String? =
        name.takeIf { value.getOrElse(false) }

    private fun stringOption(name: String, value: Provider<String>): String? =
        value.orNull?.let { "$name:$it" }
}

enum class CheckSeverity {
    DEFAULT, OFF, WARN, ERROR;

    internal val asArg: String
        get() = if (this == DEFAULT) "" else ":$name"
}

// Extensions
val CompileOptions.errorprone: ErrorProneOptions
    get() = (this as ExtensionAware).extensions.getByName<ErrorProneOptions>(ErrorProneOptions.NAME)

fun CompileOptions.errorprone(action: Action<in ErrorProneOptions>) =
    (this as ExtensionAware).extensions.configure(ErrorProneOptions.NAME, action)
