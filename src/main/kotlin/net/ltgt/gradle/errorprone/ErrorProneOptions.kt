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
    @get:JvmName("getEnabled")
    @get:Input val isEnabled = objectFactory.property<Boolean>().convention(false)
    @get:Input val disableAllChecks = objectFactory.property<Boolean>().convention(false)
    @get:Input val allErrorsAsWarnings = objectFactory.property<Boolean>().convention(false)
    @get:Input val allDisabledChecksAsWarnings = objectFactory.property<Boolean>().convention(false)
    @get:Input val disableWarningsInGeneratedCode = objectFactory.property<Boolean>().convention(false)
    @get:Input val ignoreUnknownCheckNames = objectFactory.property<Boolean>().convention(false)
    @get:Input val ignoreSuppressionAnnotations = objectFactory.property<Boolean>().convention(false)
    @get:JvmName("getCompilingTestOnlyCode")
    @get:Input val isCompilingTestOnlyCode = objectFactory.property<Boolean>().convention(false)
    @get:Input @get:Optional val excludedPaths = objectFactory.property<String>()
    @get:Input val checks = objectFactory.mapProperty<String, CheckSeverity>().empty()
    @get:Input val checkOptions = objectFactory.mapProperty<String, String>().empty()
    @get:Input val errorproneArgs = objectFactory.listProperty<String>().empty()
    @get:Nested val errorproneArgumentProviders: MutableList<CommandLineArgumentProvider> = arrayListOf()

    companion object {
        const val NAME = "errorprone"
    }

    @Deprecated("Renamed to enable", replaceWith = ReplaceWith("enable(*checkNames)"), level = DeprecationLevel.ERROR)
    fun check(vararg checkNames: String) = enable(*checkNames)
    fun check(vararg pairs: Pair<String, CheckSeverity>) = pairs.forEach { (checkName, severity) -> check(checkName, severity) }
    fun check(checkName: String, severity: CheckSeverity) {
        validateName(checkName)
        checks.put(checkName, severity)
    }
    fun check(checkName: String, severity: Provider<CheckSeverity>) {
        validateName(checkName)
        checks.put(checkName, severity)
    }

    fun enable(vararg checkNames: String) = set(*checkNames, atSeverity = CheckSeverity.DEFAULT)
    fun disable(vararg checkNames: String) = set(*checkNames, atSeverity = CheckSeverity.OFF)
    fun warn(vararg checkNames: String) = set(*checkNames, atSeverity = CheckSeverity.WARN)
    fun error(vararg checkNames: String) = set(*checkNames, atSeverity = CheckSeverity.ERROR)

    private fun set(vararg checkNames: String, atSeverity: CheckSeverity) =
        checkNames.forEach { check(it, atSeverity) }

    @JvmOverloads fun option(name: String, value: Boolean = true) = option(name, value.toString())
    fun option(name: String, value: String) {
        checkOptions.put(name, value)
    }
    fun option(name: String, value: Provider<String>) {
        checkOptions.put(name, value)
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
                checks.getOrElse(emptyMap()).asSequence().map { (name, severity) -> validateName(name); "-Xep:$name${severity.asArg}" } +
                checkOptions.getOrElse(emptyMap()).asSequence().map { (name, value) -> "-XepOpt:$name=$value" } +
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
    DEFAULT, OFF, WARN, ERROR
}

private val CheckSeverity.asArg: String
    get() = if (this == CheckSeverity.DEFAULT) "" else ":$name"

private fun validate(arg: String) {
    if (arg.contains("""\p{IsWhite_Space}""".toRegex()))
        throw InvalidUserDataException("""Error Prone options cannot contain white space: "$arg".""")
}

private fun validateName(checkName: String) {
    if (checkName.contains(':'))
        throw InvalidUserDataException("""Error Prone check name cannot contain a colon (":"): "$checkName".""")
}

// Extensions
val CompileOptions.errorprone: ErrorProneOptions
    get() = (this as ExtensionAware).extensions.getByName<ErrorProneOptions>(ErrorProneOptions.NAME)

fun CompileOptions.errorprone(action: Action<in ErrorProneOptions>) =
    (this as ExtensionAware).extensions.configure(ErrorProneOptions.NAME, action)
