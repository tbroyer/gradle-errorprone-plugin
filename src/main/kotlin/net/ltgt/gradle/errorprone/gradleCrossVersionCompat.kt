package net.ltgt.gradle.errorprone

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.util.GradleVersion

private val SUPPORTS_LIST_PROPERTY_EMPTY = GradleVersion.current() >= GradleVersion.version("5.0")
private val SUPPORTS_PROPERTY_CONVENTION = GradleVersion.current() >= GradleVersion.version("5.1")

internal fun <T> Property<T>.byConvention(value: T) =
    if (SUPPORTS_PROPERTY_CONVENTION) {
        convention(value)
    } else {
        apply { set(value) }
    }

internal fun <T> ListProperty<T>.setEmpty() =
    if (SUPPORTS_LIST_PROPERTY_EMPTY) {
        empty()
    } else {
        // this is default behavior in Gradle 4.x
        this
    }
