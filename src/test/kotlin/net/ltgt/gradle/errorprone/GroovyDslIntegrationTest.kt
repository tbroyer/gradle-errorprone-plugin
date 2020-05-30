package net.ltgt.gradle.errorprone

import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test

class GroovyDslIntegrationTest : AbstractPluginIntegrationTest() {
    override fun setupProject() {
        settingsFile = testProjectDir.newFile("settings.gradle")
        buildFile = testProjectDir.newFile("build.gradle").apply {
            appendText(
                """
                plugins {
                    id("java-library")
                    id("${ErrorPronePlugin.PLUGIN_ID}")
                }
                repositories {
                    mavenCentral()
                }
                dependencies {
                    errorprone "com.google.errorprone:error_prone_core:$errorproneVersion"
                    errorproneJavac "com.google.errorprone:javac:$errorproneJavacVersion"
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun `can disable errorprone`() {
        // given
        buildFile.appendText(
            """

            tasks.withType(JavaCompile).configureEach {
                options.errorprone.enabled = false
            }
            """.trimIndent()
        )
        writeFailureSource()

        // when
        val result = buildWithArgs("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `can configure errorprone`() {
        // given
        buildFile.appendText(
            """

            tasks.withType(JavaCompile).configureEach {
                options.errorprone {
                    // configure with the default values, we only want to check the DSL, not the effects
                    enabled = true
                    disableAllChecks = false
                    disableAllWarnings = false
                    allErrorsAsWarnings = false
                    allDisabledChecksAsWarnings = false
                    disableWarningsInGeneratedCode = false
                    ignoreUnknownCheckNames = false
                    ignoreSuppressionAnnotations = false
                    compilingTestOnlyCode = false
                    excludedPaths = "should.not.match.anything"
                    enable("ArrayEquals")
                }
            }
            """.trimIndent()
        )
        writeSuccessSource()

        // when
        val result = buildWithArgs("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }
}
