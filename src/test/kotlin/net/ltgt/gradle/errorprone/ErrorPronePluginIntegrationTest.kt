package net.ltgt.gradle.errorprone

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class ErrorPronePluginIntegrationTest : AbstractPluginIntegrationTest() {

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
                errorproneJavac("com.google.errorprone:javac:$errorproneJavacVersion")
            }
            """.trimIndent()
        )
        if (JavaVersion.current() >= JavaVersion.VERSION_16 && GradleVersion.version(testGradleVersion) < GradleVersion.version("7.0")) {
            // https://melix.github.io/blog/2021/03/gradle-java16.html
            buildFile.appendText(
                """

                allprojects {
                    tasks.withType<JavaCompile>().configureEach {
                        options.isIncremental = false
                    }
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun `compilation succeeds`() {
        // given
        testProjectDir.writeSuccessSource()

        // when
        val result = testProjectDir.buildWithArgs("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `compilation fails`() {
        // given
        testProjectDir.writeFailureSource()

        // when
        val result = testProjectDir.buildWithArgsAndFail("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains(FAILURE_SOURCE_COMPILATION_ERROR)
    }

    @Test
    fun `can configure errorprone`() {
        // given
        buildFile.appendText(
            """

            tasks.withType<JavaCompile>().configureEach {
                options.errorprone {
                    disable("ArrayEquals")
                }
            }
            """.trimIndent()
        )
        testProjectDir.writeFailureSource()

        // when
        val result = testProjectDir.buildWithArgs("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `can disable errorprone`() {
        // given
        buildFile.appendText(
            """

            tasks.withType<JavaCompile>().configureEach {
                options.errorprone.isEnabled.set(false)
            }
            """.trimIndent()
        )
        testProjectDir.writeFailureSource()

        // when
        val result = testProjectDir.buildWithArgs("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `with a custom check`() {
        // given
        settingsFile.appendText(
            """

            include(":customCheck")
            """.trimIndent()
        )
        File(testProjectDir.resolve("customCheck").apply { mkdirs() }, "build.gradle.kts").writeText(
            """
            plugins {
                java
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                compileOnly("com.google.errorprone:error_prone_check_api:$errorproneVersion")
            }
            """.trimIndent()
        )
        File(
            testProjectDir.resolve("customCheck/src/main/resources/META-INF/services").apply { mkdirs() },
            "com.google.errorprone.bugpatterns.BugChecker"
        ).writeText("com.google.errorprone.sample.MyCustomCheck")
        File(
            testProjectDir.resolve("customCheck/src/main/java/com/google/errorprone/sample").apply { mkdirs() },
            "MyCustomCheck.java"
        ).writeText(javaClass.getResource("/com/google/errorprone/sample/MyCustomCheck.java").readText())

        buildFile.appendText(
            """

            dependencies {
                errorprone(project(":customCheck"))
            }
            tasks.withType<JavaCompile>().configureEach {
                options.errorprone.error("MyCustomCheck")
            }
            """.trimIndent()
        )

        File(
            testProjectDir.resolve("src/main/java/com/google/errorprone/sample").apply { mkdirs() },
            "Hello.java"
        ).writeText(javaClass.getResource("/com/google/errorprone/sample/Hello.java").readText())

        // when
        val result = testProjectDir.buildWithArgsAndFail("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains("[MyCustomCheck] String formatting inside print method")
    }

    @Test
    fun `is configuration-cache friendly`() {
        assume().that(
            JavaVersion.current() >= JavaVersion.VERSION_16 &&
                GradleVersion.version(testGradleVersion) < GradleVersion.version("7.0")
        ).isFalse()

        // given
        // Use a failing check to make sure that the configuration is properly persisted/reloaded
        buildFile.appendText(
            """

            tasks.withType<JavaCompile>().configureEach {
                options.errorprone {
                    disable("ArrayEquals")
                }
            }
            """.trimIndent()
        )
        testProjectDir.writeFailureSource()

        // Prime the configuration cache
        testProjectDir.buildWithArgs("--configuration-cache", "compileJava")

        // when
        val result = testProjectDir.buildWithArgs("--configuration-cache", "--rerun-tasks", "--debug", "compileJava")

        // then
        assertThat(result.output).contains("Reusing configuration cache.")
        // Check that the second run indeed used ErrorProne.
        // As it didn't fail, it means the rest of the configuration was properly persisted/reloaded.
        assertThat(result.output).contains("-Xplugin:ErrorProne")
    }
}
