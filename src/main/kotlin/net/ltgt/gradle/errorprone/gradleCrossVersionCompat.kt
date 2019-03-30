package net.ltgt.gradle.errorprone

import org.gradle.api.DomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.util.GradleVersion

private val MIN_GRADLE_VERSION_WITH_LAZY_TASKS = GradleVersion.version("4.9")

internal fun supportsLazyTasks(version: GradleVersion) = version >= MIN_GRADLE_VERSION_WITH_LAZY_TASKS

private val SUPPORTS_LAZY_TASKS = supportsLazyTasks(GradleVersion.current())
private val SUPPORTS_LIST_PROPERTY_EMPTY = GradleVersion.current() >= GradleVersion.version("5.0")
private val SUPPORTS_PROPERTY_CONVENTION = GradleVersion.current() >= GradleVersion.version("5.1")

internal inline fun <reified T : Task> Project.configureTask(taskName: String, noinline action: T.() -> Unit) {
    if (SUPPORTS_LAZY_TASKS) {
        tasks.withType(T::class.java).named(taskName).configure(action)
    } else {
        tasks.withType(T::class.java).getByName(taskName, action)
    }
}

internal fun <T> DomainObjectCollection<out T>.configureElement(action: T.() -> Unit) {
    if (SUPPORTS_LAZY_TASKS) {
        this.configureEach(action)
    } else {
        this.all(action)
    }
}

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
