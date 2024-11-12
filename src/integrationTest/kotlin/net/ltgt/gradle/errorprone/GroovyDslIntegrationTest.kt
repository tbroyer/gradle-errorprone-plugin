package net.ltgt.gradle.errorprone

import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Properties

class GroovyDslIntegrationTest {
    @TempDir
    lateinit var testProjectDir: File
    lateinit var settingsFile: File
    lateinit var buildFile: File

    @BeforeEach
    fun setupProject() {
        assumeCompatibleGradleAndJavaVersions()

        testProjectDir.resolve("gradle.properties").outputStream().use {
            Properties().apply {
                setProperty("org.gradle.java.home", testJavaHome)
                store(it, null)
            }
        }
        settingsFile =
            testProjectDir.resolve("settings.gradle").apply {
                createNewFile()
            }
        buildFile =
            testProjectDir.resolve("build.gradle").apply {
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
                    }
                    """.trimIndent(),
                )
            }
        if (testGradleVersion < GradleVersion.version("7.0")) {
            buildFile.appendText(
                """

                allprojects {
                    configurations.all {
                        attributes.attribute(Attribute.of("org.gradle.jvm.environment", String), "standard-jvm")
                    }
                }
                """.trimIndent(),
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
            """.trimIndent(),
        )
        testProjectDir.writeFailureSource()

        // when
        val result = testProjectDir.buildWithArgs("compileJava")

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
            """.trimIndent(),
        )
        testProjectDir.writeSuccessSource()

        // when
        val result = testProjectDir.buildWithArgs("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }
}
