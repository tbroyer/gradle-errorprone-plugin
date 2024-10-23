package net.ltgt.gradle.errorprone

import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Test

class ManualConfigurationIntegrationTest : AbstractPluginIntegrationTest() {

    @Test
    fun `in non-java project with applied plugin`() {
        // given
        buildFile.appendText(
            """
            plugins {
                id("${ErrorPronePlugin.PLUGIN_ID}")
                `jvm-ecosystem`
            }

            repositories {
                mavenCentral()
            }
            dependencies {
                errorprone("com.google.errorprone:error_prone_core:$errorproneVersion")
            }
            val annotationProcessor ${
                if (testGradleVersion >= GradleVersion.version("8.4")) {
                    """= configurations.resolvable("annotationProcessor") {"""
                } else {
                    """by configurations.registering {
                isCanBeConsumed = false
                isCanBeResolved = true"""
                }}
                extendsFrom(configurations["errorprone"])
            }

            val compileJava by tasks.creating(JavaCompile::class) {
                source("src/main/java")
                classpath = files()
                destinationDir = file("${'$'}buildDir/classes")
                sourceCompatibility = "8"
                targetCompatibility = "8"
                options.annotationProcessorPath = annotationProcessor.get()

                options.errorprone {
                    isEnabled.set(true)
                    disableAllChecks.set(true)
                    error("ArrayEquals")
                }
            }
            """.trimIndent(),
        )
        testProjectDir.writeFailureSource()

        // when
        val result = testProjectDir.buildWithArgsAndFail("--info", "compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains(FAILURE_SOURCE_COMPILATION_ERROR)
    }

    @Test
    fun `in java project`() {
        // This is similar to what the me.champeau.gradle.jmh plugin does

        // given
        buildFile.appendText(
            """
            plugins {
                java
                id("${ErrorPronePlugin.PLUGIN_ID}")
            }

            repositories {
                mavenCentral()
            }
            dependencies {
                errorprone("com.google.errorprone:error_prone_core:$errorproneVersion")
            }
            val customAnnotationProcessor ${
                if (testGradleVersion >= GradleVersion.version("8.4")) {
                    """= configurations.resolvable("customAnnotationProcessor") {"""
                } else {
                    """by configurations.registering {
                isCanBeConsumed = false
                isCanBeResolved = true"""
                }}
                extendsFrom(configurations["errorprone"])
            }

            val customCompileJava by tasks.creating(JavaCompile::class) {
                source("src/main/java")
                classpath = files()
                destinationDir = file("${'$'}buildDir/classes/custom")
                options.annotationProcessorPath = customAnnotationProcessor.get()

                options.errorprone {
                    disableAllChecks.set(true)
                    error("ArrayEquals")
                }
            }
            """.trimIndent(),
        )
        testProjectDir.writeFailureSource()

        // Error Prone is disabled by default, so compilation should succeed.

        // when
        val result = testProjectDir.buildWithArgs("customCompileJava")

        // then
        assertThat(result.task(":customCompileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        // Now enable Error Prone and check that compilation fails.

        // given
        buildFile.appendText(
            """

            customCompileJava.options.errorprone.isEnabled.set(true)
            """.trimIndent(),
        )

        // when
        val result2 = testProjectDir.buildWithArgsAndFail("customCompileJava")

        // then
        assertThat(result2.task(":customCompileJava")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result2.output).contains(FAILURE_SOURCE_COMPILATION_ERROR)
    }
}
