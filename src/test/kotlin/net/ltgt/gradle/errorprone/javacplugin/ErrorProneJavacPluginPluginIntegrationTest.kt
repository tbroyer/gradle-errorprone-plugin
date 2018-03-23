package net.ltgt.gradle.errorprone.javacplugin

import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Before
import org.junit.Test

class ErrorProneJavacPluginPluginIntegrationTest : AbstractPluginIntegrationTest() {

    @Before
    fun setup() {
        buildFile.appendText("""
            plugins {
                `java-library`
                id("${ErrorProneJavacPluginPlugin.PLUGIN_ID}")
            }
            repositories {
                jcenter()
            }
            dependencies {
                errorprone("com.google.errorprone:error_prone_core:$errorproneVersion")
            }
        """.trimIndent())
    }

    @Test
    fun `compilation succeeds`() {
        // given
        writeSuccessSource()

        // when
        val result = buildWithArgs("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `compilation fails`() {
        // given
        writeFailureSource()

        // when
        val result = buildWithArgsAndFail("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains("Failure.java:6: error: [ArrayEquals]")
    }

    @Test
    fun `can configure errorprone`() {
        // given
        buildFile.appendText("""

            tasks.withType<JavaCompile>() {
                options.errorprone {
                    check("ArrayEquals", CheckSeverity.OFF)
                }
            }
        """.trimIndent())
        writeFailureSource()

        // when
        val result = buildWithArgs("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `can disable errorprone`() {
        // given
        buildFile.appendText("""

            tasks.withType<JavaCompile>() {
                options.errorprone.isEnabled = false
            }
        """.trimIndent())
        writeFailureSource()

        // when
        val result = buildWithArgs("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `can configure manually`() {
        // given
        buildFile.writeText("""
            import net.ltgt.gradle.errorprone.javacplugin.*

            plugins {
                java
                id("${ErrorProneJavacPluginPlugin.PLUGIN_ID}") apply false
            }

            repositories {
                jcenter()
            }
            dependencies {
                annotationProcessor("com.google.errorprone:error_prone_core:$errorproneVersion")
            }

            val compileJava by tasks.getting(JavaCompile::class) {
                ErrorProneJavacPlugin.apply(options)
                options.errorprone.check("ArrayEquals", CheckSeverity.OFF)
            }
        """.trimIndent())
        writeFailureSource()

        // when
        val result = buildWithArgs("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }
}
