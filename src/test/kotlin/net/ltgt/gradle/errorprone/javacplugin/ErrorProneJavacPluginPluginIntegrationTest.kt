package net.ltgt.gradle.errorprone.javacplugin

import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Before
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

                    compileOnly("com.google.auto.service:auto-service:1.0-rc4")
                    annotationProcessor("com.google.auto.service:auto-service:1.0-rc4")
                }
            """.trimIndent())
        File(
            testProjectDir.newFolder("customCheck", "src", "main", "resources", "META-INF", "services"),
            "com.google.errorprone.bugpatterns.BugChecker"
        ).writeText("com.google.errorprone.sample.MyCustomCheck")
        File(
            testProjectDir.newFolder("customCheck", "src", "main", "java", "com", "google", "errorprone", "sample"),
            "MyCustomCheck.java"
        ).writeText(javaClass.getResource("/com/google/errorprone/sample/MyCustomCheck.java").readText())

        buildFile.appendText("""

            dependencies {
                errorprone(project(":customCheck"))
            }
            tasks.withType<JavaCompile> {
                options.errorprone.check("MyCustomCheck", CheckSeverity.ERROR)
            }
        """.trimIndent())

        File(
            testProjectDir.newFolder("src", "main", "java", "com", "google", "errorprone", "sample"),
            "Hello.java"
        ).writeText(javaClass.getResource("/com/google/errorprone/sample/Hello.java").readText())

        // when
        val result = buildWithArgsAndFail("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains("[MyCustomCheck] String formatting inside print method")
    }
}
