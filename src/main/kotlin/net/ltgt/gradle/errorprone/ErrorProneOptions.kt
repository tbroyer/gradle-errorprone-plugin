package net.ltgt.gradle.errorprone

import org.gradle.api.Action
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.kotlin.dsl.getByName

val CompileOptions.errorprone: ErrorProneOptions
    get() = (this as ExtensionAware).extensions.getByName<ErrorProneOptions>(ErrorProneOptions.NAME)

fun CompileOptions.errorprone(action: Action<in ErrorProneOptions>) =
    (this as ExtensionAware).extensions.configure(ErrorProneOptions.NAME, action)
