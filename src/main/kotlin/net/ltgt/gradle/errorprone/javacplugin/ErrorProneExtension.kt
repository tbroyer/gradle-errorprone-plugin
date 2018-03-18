package net.ltgt.gradle.errorprone.javacplugin

import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.kotlin.dsl.getByName

open class ErrorProneExtension(@get:Input var errorproneArgs: MutableList<String> = arrayListOf()) {
    companion object {
        const val NAME = "errorproneOptions"
    }
}

val CompileOptions.errorproneOptions: ErrorProneExtension
    get() = DslObject(this).extensions.getByName<ErrorProneExtension>(ErrorProneExtension.NAME)

operator fun ErrorProneExtension.invoke(configure: ErrorProneExtension.() -> Unit): ErrorProneExtension =
    apply(configure)
