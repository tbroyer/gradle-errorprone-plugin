package net.ltgt.gradle.errorprone.javacplugin

import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.kotlin.dsl.* // ktlint-disable no-wildcard-imports
import org.gradle.process.CommandLineArgumentProvider
import java.util.StringJoiner

open class ErrorProneOptions(
    @get:Input var isEnabled: Boolean = true,
    @get:Input var disableAllChecks: Boolean = false,
    @get:Input var allErrorsAsWarnings: Boolean = false,
    @get:Input var allDisabledChecksAsWarnings: Boolean = false,
    @get:Input var disableWarningsInGeneratedCode: Boolean = false,
    @get:Input var ignoreUnknownCheckNames: Boolean = false,
    @get:Input var isCompilingTestOnlyCode: Boolean = false,
    @get:Input @get:Optional var excludedPaths: String? = null,
    @get:Input var checks: MutableMap<String, CheckSeverity> = linkedMapOf(),
    @get:Input var checkOptions: MutableMap<String, String> = linkedMapOf(),
    @get:Input var errorproneArgs: MutableList<String> = arrayListOf(),
    @get:Nested val errorproneArgumentProviders: MutableList<CommandLineArgumentProvider> = arrayListOf()
) {
    companion object {
        const val NAME = "errorprone"
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
        val joiner = StringJoiner(" ").add("-Xplugin:ErrorProne")
        if (disableAllChecks) joiner.add("-XepDisableAllChecks")
        if (allErrorsAsWarnings) joiner.add("-XepAllErrorsAsWarnings")
        if (allDisabledChecksAsWarnings) joiner.add("-XepDisabledChecksAsWarnings")
        if (disableWarningsInGeneratedCode) joiner.add("-XepDisableWarningsInGeneratedCode")
        if (ignoreUnknownCheckNames) joiner.add("-XepIgnoreUnknownCheckNames")
        if (isCompilingTestOnlyCode) joiner.add("-XepCompilingTestOnlyCode")
        if (!excludedPaths.isNullOrEmpty()) joiner.add("-XepExcludedPaths:$excludedPaths")
        checks.forEach { (name, severity) -> joiner.add("-Xep:$name${severity.asArg}") }
        checkOptions.forEach { name, value -> joiner.add("-XepOpt:$name=$value") }
        errorproneArgs.forEach { joiner.add(it) }
        errorproneArgumentProviders.forEach { it.asArguments().forEach { joiner.add(it) } }
        return joiner.toString()
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
