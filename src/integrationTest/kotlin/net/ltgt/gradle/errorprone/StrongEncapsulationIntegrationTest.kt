package net.ltgt.gradle.errorprone

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StrongEncapsulationIntegrationTest : AbstractPluginIntegrationTest() {
    companion object {
        private val FORKED = "${System.lineSeparator()}Fork: true${System.lineSeparator()}"
        private val NOT_FORKED = "${System.lineSeparator()}Fork: false${System.lineSeparator()}"
        private val JVM_ARG = "${System.lineSeparator()}JVM Arg: "
        private val JVM_ARGS_STRONG_ENCAPSULATION =
            ErrorPronePlugin.JVM_ARGS_STRONG_ENCAPSULATION.joinToString(
                prefix = JVM_ARG,
                separator = JVM_ARG,
            )

        private fun jvmArg(argPrefix: String) = "$JVM_ARG$argPrefix"
    }

    @BeforeEach
    fun setup() {
        buildFile.appendText(
            """
            plugins {
                `java-library`
                id("${ErrorPronePlugin.PLUGIN_ID}")
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                errorprone("com.google.errorprone:error_prone_core:$errorproneVersion")
            }

            val compileJava: JavaCompile by tasks
            val displayCompileJavaOptions by tasks.creating {
                doFirst {
                    println("Fork: ${'$'}{compileJava.options.isFork}")
                    compileJava.options.forkOptions.allJvmArgs.forEach { arg ->
                        println("JVM Arg: ${'$'}arg")
                    }
                }
            }
            compileJava.finalizedBy(displayCompileJavaOptions)
            compileJava.options.forkOptions.jvmArgs!!.add("-XshowSettings")
            """.trimIndent(),
        )
        testProjectDir.writeSuccessSource()
    }

    @Test
    fun `does not configure forking in Java before 16`() {
        assume().withMessage("isJava16Compatible").that(testJavaVersion).isLessThan(JavaVersion.VERSION_16)

        // when
        testProjectDir.buildWithArgs("compileJava").also { result ->
            // then
            assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.output).contains(NOT_FORKED)
            // Check that the configured jvm arg is preserved
            assertThat(result.output).contains(jvmArg("-XshowSettings"))
        }

        // Test a forked task

        // given
        buildFile.appendText(
            """

            compileJava.options.isFork = true
            """.trimIndent(),
        )

        // when
        testProjectDir.buildWithArgs("compileJava").also { result ->
            // then
            assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.output).contains(FORKED)
            assertThat(result.output).contains(JVM_ARGS_STRONG_ENCAPSULATION)
            // Check that the configured jvm arg is preserved
            assertThat(result.output).contains(jvmArg("-XshowSettings"))
        }
    }

    @Test
    fun `configure forking in Java 16+ VM`() {
        assume().withMessage("isJava16Compatible").that(testJavaVersion).isAtLeast(JavaVersion.VERSION_16)

        // when
        testProjectDir.buildWithArgs("compileJava").also { result ->
            // then
            assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.output).contains(FORKED)
            assertThat(result.output).contains(JVM_ARGS_STRONG_ENCAPSULATION)
            // Check that the configured jvm arg is preserved
            assertThat(result.output).contains(jvmArg("-XshowSettings"))
        }

        // check that it doesn't mess with task avoidance

        // when
        testProjectDir.buildWithArgs("compileJava").also { result ->
            // then
            assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        }
    }

    @Test
    fun `does not configure forking if Error Prone is disabled`() {
        assume().withMessage("isJava16Compatible").that(testJavaVersion).isAtLeast(JavaVersion.VERSION_16)

        // given
        buildFile.appendText(
            """

            compileJava.options.errorprone.enabled.set(false)
            """.trimIndent(),
        )

        // when
        val result = testProjectDir.buildWithArgs("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).contains(NOT_FORKED)
        // Check that the configured jvm arg is preserved
        assertThat(result.output).contains(jvmArg("-XshowSettings"))
    }
}
