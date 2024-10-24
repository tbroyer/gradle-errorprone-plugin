package net.ltgt.gradle.errorprone

import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.TaskOutcome
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
            }
            """.trimIndent(),
        )
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
            """.trimIndent(),
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
            """.trimIndent(),
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
            """.trimIndent(),
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
            ${if (testJavaVersion.isJava8) {
                ""
            } else {
                """
                tasks {
                    compileJava {
                        options.compilerArgs.addAll(listOf(
                            "--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
                            "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
                            "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
                            ))
                    }
                }
                """.trimIndent()
            }}
            """.trimIndent(),
        )
        File(
            testProjectDir.resolve("customCheck/src/main/resources/META-INF/services").apply { mkdirs() },
            "com.google.errorprone.bugpatterns.BugChecker",
        ).writeText("com.google.errorprone.sample.MyCustomCheck")
        File(
            testProjectDir.resolve("customCheck/src/main/java/com/google/errorprone/sample").apply { mkdirs() },
            "MyCustomCheck.java",
        ).writeText(javaClass.getResource("/com/google/errorprone/sample/MyCustomCheck.java")!!.readText())

        buildFile.appendText(
            """

            dependencies {
                errorprone(project(":customCheck"))
            }
            tasks.withType<JavaCompile>().configureEach {
                options.errorprone.error("MyCustomCheck")
            }
            """.trimIndent(),
        )

        File(
            testProjectDir.resolve("src/main/java/com/google/errorprone/sample").apply { mkdirs() },
            "Hello.java",
        ).writeText(javaClass.getResource("/com/google/errorprone/sample/Hello.java")!!.readText())

        // when
        val result = testProjectDir.buildWithArgsAndFail("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains("[MyCustomCheck] String formatting inside print method")
    }

    @Test
    fun `is configuration-cache friendly`() {
        // given
        // Use a failing check to make sure that the configuration is properly persisted/reloaded
        buildFile.appendText(
            """

            tasks.withType<JavaCompile>().configureEach {
                options.errorprone {
                    disable("ArrayEquals")
                }
            }
            """.trimIndent(),
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

    // Inspired by the tests added in Error Prone's https://github.com/google/error-prone/pull/4618
    @Test
    fun `should-stop ifError`() {
        // given
        settingsFile.appendText(
            """

            include(":customCheck")
            """.trimIndent(),
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
            ${if (testJavaVersion.isJava8) {
                ""
            } else {
                """
                tasks {
                    compileJava {
                        options.compilerArgs.addAll(listOf(
                            "--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
                            "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
                            "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
                            ))
                    }
                }
                """.trimIndent()
            }}
            """.trimIndent(),
        )
        File(
            testProjectDir.resolve("customCheck/src/main/resources/META-INF/services").apply { mkdirs() },
            "com.google.errorprone.bugpatterns.BugChecker",
        ).writeText(
            """
                com.google.errorprone.sample.CPSChecker
                com.google.errorprone.sample.EffectivelyFinalChecker
            """.trimIndent(),
        )

        File(
            testProjectDir.resolve("customCheck/src/main/java/com/google/errorprone/sample").apply { mkdirs() },
            "CPSChecker.java",
        ).writeText(javaClass.getResource("/com/google/errorprone/sample/CPSChecker.java")!!.readText())
        File(
            testProjectDir.resolve("customCheck/src/main/java/com/google/errorprone/sample").apply { mkdirs() },
            "EffectivelyFinalChecker.java",
        ).writeText(javaClass.getResource("/com/google/errorprone/sample/EffectivelyFinalChecker.java")!!.readText())

        buildFile.appendText(
            """

            dependencies {
                errorprone(project(":customCheck"))
            }
            tasks.withType<JavaCompile>().configureEach {
                options.errorprone.error("CPSChecker", "EffectivelyFinalChecker")
            }
            """.trimIndent(),
        )

        File(
            testProjectDir.resolve("src/main/java/com/google/errorprone/sample").apply { mkdirs() },
            "A.java",
        ).writeText(
            """
            package com.google.errorprone.sample;

            class A {
              int f(int x) {
                return x;
              }
            }
            """.trimIndent(),
        )
        File(
            testProjectDir.resolve("src/main/java/com/google/errorprone/sample").apply { mkdirs() },
            "B.java",
        ).writeText(
            """
            package com.google.errorprone.sample;

            class B {
              int f(int x) {
                return x;
              }
            }
            """.trimIndent(),
        )

        // when
        val result = testProjectDir.buildWithArgsAndFail("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains("A.java:5: error: [CPSChecker]")
        assertThat(result.output).contains("B.java:5: error: [CPSChecker]")
        assertThat(result.output).doesNotContain("[EffectivelyFinalChecker]")
    }
}
