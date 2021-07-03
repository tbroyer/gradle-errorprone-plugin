package net.ltgt.gradle.errorprone

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GroovyDslIntegrationTest {

    @JvmField
    @Rule
    val testProjectDir = TemporaryFolder()

    lateinit var settingsFile: File
    lateinit var buildFile: File

    @Before
    fun setupProject() {
        assume().that(
            JavaVersion.current().isJava16Compatible &&
                GradleVersion.version(testGradleVersion) < GradleVersion.version("7.0")
        ).isFalse()

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
        testProjectDir.root.writeFailureSource()

        // when
        val result = testProjectDir.root.buildWithArgs("compileJava")

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
        testProjectDir.root.writeSuccessSource()

        // when
        val result = testProjectDir.root.buildWithArgs("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }
}
