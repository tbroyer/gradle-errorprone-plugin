package net.ltgt.gradle.errorprone.javacplugin

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.kotlin.dsl.withConvention

data class ErrorProneConvention(@get:Input var errorproneArgs: MutableList<String> = arrayListOf())

var CompileOptions.errorproneArgs: MutableList<String>
    get() = withConvention(ErrorProneConvention::class) { errorproneArgs }
    set(value) {
        withConvention(ErrorProneConvention::class) { errorproneArgs = value }
    }
