package net.ltgt.gradle.errorprone

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import org.junit.Before
import org.junit.Test
import java.io.File

class ErrorPronePluginIntegrationTest : AbstractPluginIntegrationTest() {

    @Before
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
        if (JavaVersion.current().isJava16Compatible && GradleVersion.version(testGradleVersion) < GradleVersion.version("7.0")) {
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
        writeFailureSource()

        // when
        val result = buildWithArgs("compileJava")

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
        writeFailureSource()

        // when
        val result = buildWithArgs("compileJava")

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
        File(testProjectDir.newFolder("customCheck"), "build.gradle.kts").writeText(
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
            testProjectDir.newFolder("customCheck", "src", "main", "resources", "META-INF", "services"),
            "com.google.errorprone.bugpatterns.BugChecker"
        ).writeText("com.google.errorprone.sample.MyCustomCheck")
        File(
            testProjectDir.newFolder("customCheck", "src", "main", "java", "com", "google", "errorprone", "sample"),
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
            testProjectDir.newFolder("src", "main", "java", "com", "google", "errorprone", "sample"),
            "Hello.java"
        ).writeText(javaClass.getResource("/com/google/errorprone/sample/Hello.java").readText())

        // when
        val result = buildWithArgsAndFail("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains("[MyCustomCheck] String formatting inside print method")
    }

    @Test
    fun `is configuration-cache friendly`() {
        assume().that(GradleVersion.version(testGradleVersion)).isAtLeast(GradleVersion.version("6.6"))
        assume().that(
            JavaVersion.current().isJava16Compatible &&
                GradleVersion.version(testGradleVersion) < GradleVersion.version("7.0")
        ).isFalse()

        // given
        writeSuccessSource()

        // Prime the configuration cache
        buildWithArgs("--configuration-cache", "compileJava")

        // when
        val result = buildWithArgs("--configuration-cache", "compileJava")

        // then
        assertThat(result.output).contains("Reusing configuration cache.")
    }
}
