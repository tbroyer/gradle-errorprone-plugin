package net.ltgt.gradle.errorprone.javacplugin

import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.io.File

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

    @Ignore("https://github.com/google/error-prone/issues/974")
    @Test
    fun `with a custom check`() {
        // given
        settingsFile.appendText("""

            include(":customCheck")
        """.trimIndent())
        File(testProjectDir.newFolder("customCheck"), "build.gradle.kts").writeText("""
                plugins {
                    java
                }
                repositories {
                    jcenter()
                }
                dependencies {
                    compileOnly("com.google.errorprone:error_prone_check_api:$errorproneVersion")
                }
            """.trimIndent())
        File(testProjectDir.newFolder("customCheck", "src", "main", "resources", "META-INF", "services"),
            "com.google.errorprone.bugpatterns.BugChecker").writeText("""
                customCheck.CustomCheck
            """.trimIndent())
        File(testProjectDir.newFolder("customCheck", "src", "main", "java", "customCheck"),
            "CustomCheck.java").writeText("""
                package customCheck;

                import com.google.errorprone.BugPattern;
                import com.google.errorprone.ErrorProneFlags;
                import com.google.errorprone.bugpatterns.BugChecker;

                @BugPattern(
                    name = "Custom",
                    summary = "Custom check",
                    severity = BugPattern.SeverityLevel.WARNING
                )
                public class CustomCheck extends BugChecker {
                    public CustomCheck() {
                        // required for ServiceLoader discovery
                    }

                    public CustomCheck(ErrorProneFlags flags) {
                        flags.getBoolean("CustomFlag").orElseThrow(() ->
                            new IllegalArgumentException("Missing flag CustomFlag"));
                    }
                }
            """.trimIndent())

        buildFile.appendText("""

            dependencies {
                errorprone(project(":customCheck"))
            }
            tasks.withType<JavaCompile> {
                options.errorprone.check("Custom", CheckSeverity.ERROR)
            }
        """.trimIndent())

        writeSuccessSource()

        // when
        var result = buildWithArgsAndFail("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains("Missing flag CustomFlag")
    }
}
