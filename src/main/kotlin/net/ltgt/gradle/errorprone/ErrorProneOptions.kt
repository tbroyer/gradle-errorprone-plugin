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
    objectFactory: ObjectFactory,
) {
    /**
     * Allows disabling Error Prone altogether for the task.
     *
     * Error Prone will still be in the [annotation processor path][CompileOptions.annotationProcessorPath],
     * but `-Xplugin:ErrorProne` won't be passed as a [compiler argument][CompileOptions.compilerArgs].
     *
     * Defaults to `true` on [tasks for source sets][org.gradle.api.tasks.SourceSet.getCompileJavaTaskName],
     * `false` otherwise.
     */
    @get:JvmName("getEnabled")
    @get:Input
    val isEnabled = objectFactory.property<Boolean>().convention(false)

    /**
     * Disable all Error Prone checks; maps to `-XepDisableAllChecks.
     *
     * This will be the first argument, so checks can then be re-enabled on a case-by-case basis.
     */
    @get:Input val disableAllChecks = objectFactory.property<Boolean>().convention(false)

    /**
     * Disables all Error Prone warnings; maps to `-XepDisableAllWarnings (since ErrorProne 2.4.0).
     *
     * This will be among the first arguments, so checks can then be re-enabled on a case-by-case basis.
     */
    @get:Input val disableAllWarnings = objectFactory.property<Boolean>().convention(false)

    /**
     * Turns all Error Prone errors into warnings; maps to `-XepAllErrorsAsWarnings`.
     *
     * This will be among the first arguments, so checks can then be promoted back to error on a case-by-case basis.
     */
    @get:Input val allErrorsAsWarnings = objectFactory.property<Boolean>().convention(false)

    /**
     * Enables all Error Prone checks, checks that are disabled by default are enabled as warnings; maps to `-XepAllDisabledChecksAsWarnings`.
     *
     * This will be among the first arguments, so checks can then be disabled again on a case-by-case basis.
     */
    @get:Input val allDisabledChecksAsWarnings = objectFactory.property<Boolean>().convention(false)

    /** Disables warnings in classes annotated with `javax.annotation.processing.Generated` or `@javax.annotation.Generated`; maps to `-XepDisableWarningsInGeneratedCode`. */
    @get:Input val disableWarningsInGeneratedCode = objectFactory.property<Boolean>().convention(false)

    /** Tells Error Prone to ignore unknown check names in [checks]; maps to `-XepIgnoreUknownCheckNames`. */
    @get:Input val ignoreUnknownCheckNames = objectFactory.property<Boolean>().convention(false)

    /** Ignores suppression annotations, such as `@SuppressWarnings`; maps to `-XepIgnoreSuppressionAnnotations` (since Error Prone 2.3.3). */
    @get:Input val ignoreSuppressionAnnotations = objectFactory.property<Boolean>().convention(false)

    /**
     * Tells Error Prone that the compilation contains only test code; maps to `-XepCompilingTestOnlyCode`.
     *
     * Defaults to `true` for a source set inferred as a test source set, `false` otherwise.
     */
    @get:JvmName("getCompilingTestOnlyCode")
    @get:Input
    val isCompilingTestOnlyCode = objectFactory.property<Boolean>().convention(false)

    /** A regular expression pattern (as a string) of file paths to exclude from Error Prone checking; maps to `-XepExcludedPaths`. */
    @get:Input @get:Optional
    val excludedPaths = objectFactory.property<String>()

    /**
     * A map of check name to [CheckSeverity], to configure which checks are enabled or disabled, and their severity.
     *
     * Maps each entry to `-Xep:<key>:<value>`, or `-Xep:<key>` when the value is [CheckSeverity.DEFAULT].
     *
     * @see check
     * @see enable
     * @see disable
     * @see error
     * @see warn
     */
    @get:Input val checks = objectFactory.mapProperty<String, CheckSeverity>().empty()

    /**
     * A map of [check options](https://errorprone.info/docs/flags#pass-additional-info-to-bugcheckers) to their value.
     *
     * Use an explicit `"true"` value for a boolean option.
     *
     * Maps each entry to `-XepOpt:<key>=<value>`.
     *
     * @see option
     */
    @get:Input val checkOptions = objectFactory.mapProperty<String, String>().empty()

    /** Additional arguments passed to Error Prone. */
    @get:Input val errorproneArgs = objectFactory.listProperty<String>().empty()

    /** A list of [CommandLineArgumentProvider] for additional arguments passed to Error Prone. */
    @get:Nested val errorproneArgumentProviders: MutableList<CommandLineArgumentProvider> = arrayListOf()

    companion object {
        const val NAME = "errorprone"
    }

    @Deprecated("Renamed to enable", replaceWith = ReplaceWith("enable(*checkNames)"), level = DeprecationLevel.ERROR)
    fun check(vararg checkNames: String) = enable(*checkNames)

    /**
     * Adds pairs of check name to severity.
     *
     * Equivalent to calling `check(first, second)` for each pair.
     *
     * @see checks
     */
    fun check(vararg pairs: Pair<String, CheckSeverity>) = pairs.forEach { (checkName, severity) -> check(checkName, severity) }

    /**
     * Adds a check with a given severity.
     *
     * Equivalent to `checks.put(checkName, severity)`.
     *
     * @see checks
     */
    fun check(checkName: String, severity: CheckSeverity) {
        validateName(checkName)
        checks.put(checkName, severity)
    }

    /**
     * Adds a check with a given severity.
     *
     * Equivalent to `checks.put(checkName, severity)`.
     *
     * @see checks
     */
    fun check(checkName: String, severity: Provider<CheckSeverity>) {
        validateName(checkName)
        checks.put(checkName, severity)
    }

    /**
     * Adds checks with their default severity.
     *
     * Useful in combination with `disableAllChecks` to selectively re-enable checks.
     *
     * Equivalent to calling `check(checkName, CheckSeverity.DEFAULT)` for each [check name][checkNames].
     *
     * @see check
     * @see checks
     */
    fun enable(vararg checkNames: String) = set(*checkNames, atSeverity = CheckSeverity.DEFAULT)

    /**
     * Disable checks.
     *
     * Equivalent to calling `check(checkName, CheckSeverity.OFF)` for each [check name][checkNames].
     *
     * @see check
     * @see checks
     */
    fun disable(vararg checkNames: String) = set(*checkNames, atSeverity = CheckSeverity.OFF)

    /**
     * Adds checks with warning severity.
     *
     * Equivalent to calling `check(checkName, CheckSeverity.WARN)` for each [check name][checkNames].
     *
     * @see check
     * @see checks
     */
    fun warn(vararg checkNames: String) = set(*checkNames, atSeverity = CheckSeverity.WARN)

    /**
     * Adds checks with error severity.
     *
     * Equivalent to calling `check(checkName, CheckSeverity.ERROR)` for each [check name][checkNames].
     *
     * @see check
     * @see checks
     */
    fun error(vararg checkNames: String) = set(*checkNames, atSeverity = CheckSeverity.ERROR)

    private fun set(vararg checkNames: String, atSeverity: CheckSeverity) =
        checkNames.forEach { check(it, atSeverity) }

    /**
     * Adds a check option with a given boolean value.
     *
     * When [value] is omitted, it defaults to `true`.
     *
     * Equivalent to `checkOptions.put(name, value.toString())`.
     *
     * @see checkOptions
     */
    @JvmOverloads fun option(name: String, value: Boolean = true) = option(name, value.toString())

    /**
     * Adds a check option with a given value.
     *
     * Equivalent to `checkOptions.put(name, value)`.
     *
     * @see checkOptions
     */
    fun option(name: String, value: String) {
        checkOptions.put(name, value)
    }

    /**
     * Adds a check option with a given value.
     *
     * Equivalent to `checkOptions.put(name, value)`.
     *
     * @see checkOptions
     */
    fun option(name: String, value: Provider<String>) {
        checkOptions.put(name, value)
    }

    override fun toString(): String {
        return (
            sequenceOf(
                booleanOption("-XepDisableAllChecks", disableAllChecks),
                booleanOption("-XepDisableAllWarnings", disableAllWarnings),
                booleanOption("-XepAllErrorsAsWarnings", allErrorsAsWarnings),
                booleanOption("-XepAllDisabledChecksAsWarnings", allDisabledChecksAsWarnings),
                booleanOption("-XepDisableWarningsInGeneratedCode", disableWarningsInGeneratedCode),
                booleanOption("-XepIgnoreUnknownCheckNames", ignoreUnknownCheckNames),
                booleanOption("-XepIgnoreSuppressionAnnotations", ignoreSuppressionAnnotations),
                booleanOption("-XepCompilingTestOnlyCode", isCompilingTestOnlyCode),
                stringOption("-XepExcludedPaths", excludedPaths),
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
    if (arg.contains("""\p{IsWhite_Space}""".toRegex())) {
        throw InvalidUserDataException("""Error Prone options cannot contain white space: "$arg".""")
    }
}

private fun validateName(checkName: String) {
    if (checkName.contains(':')) {
        throw InvalidUserDataException("""Error Prone check name cannot contain a colon (":"): "$checkName".""")
    }
}

// Extensions
val CompileOptions.errorprone: ErrorProneOptions
    get() = (this as ExtensionAware).extensions.getByName<ErrorProneOptions>(ErrorProneOptions.NAME)

fun CompileOptions.errorprone(action: Action<in ErrorProneOptions>) =
    (this as ExtensionAware).extensions.configure(ErrorProneOptions.NAME, action)
