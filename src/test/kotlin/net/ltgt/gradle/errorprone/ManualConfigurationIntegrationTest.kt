package net.ltgt.gradle.errorprone

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import org.junit.Test

class ManualConfigurationIntegrationTest : AbstractPluginIntegrationTest() {

    @Test
    fun `in non-java project with applied plugin`() {
        // jvm-ecosystem plugin has been added in Gradle 6.7
        assume().that(GradleVersion.version(testGradleVersion).baseVersion).isAtLeast(GradleVersion.version("6.7"))

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
                errorproneJavac("com.google.errorprone:javac:$errorproneJavacVersion")
            }

            val compileJava by tasks.creating(JavaCompile::class) {
                source("src/main/java")
                classpath = files()
                destinationDir = file("${'$'}buildDir/classes")
                sourceCompatibility = "8"
                targetCompatibility = "8"
                options.annotationProcessorPath = configurations["errorprone"]

                options.errorprone {
                    isEnabled.set(true)
                    disableAllChecks.set(true)
                    error("ArrayEquals")
                }
            }
            """.trimIndent()
        )
        if (JavaVersion.current().isJava16Compatible && GradleVersion.version(testGradleVersion) < GradleVersion.version("7.0")) {
            // https://melix.github.io/blog/2021/03/gradle-java16.html
            buildFile.appendText(
                """

                tasks.withType<JavaCompile>().configureEach {
                    options.isIncremental = false
                }
                """.trimIndent()
            )
        }
        writeFailureSource()

        // when
        val result = buildWithArgsAndFail("--info", "compileJava")

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
                errorproneJavac("com.google.errorprone:javac:$errorproneJavacVersion")
            }

            val customCompileJava by tasks.creating(JavaCompile::class) {
                source("src/main/java")
                classpath = files()
                destinationDir = file("${'$'}buildDir/classes/custom")
                options.annotationProcessorPath = configurations["errorprone"]

                options.errorprone {
                    disableAllChecks.set(true)
                    error("ArrayEquals")
                }
            }
            """.trimIndent()
        )
        if (JavaVersion.current().isJava16Compatible && GradleVersion.version(testGradleVersion) < GradleVersion.version("7.0")) {
            // https://melix.github.io/blog/2021/03/gradle-java16.html
            buildFile.appendText(
                """

                tasks.withType<JavaCompile>().configureEach {
                    options.isIncremental = false
                }
                """.trimIndent()
            )
        }
        writeFailureSource()

        // Error Prone is disabled by default, so compilation should succeed.

        // when
        val result = buildWithArgs("customCompileJava")

        // then
        assertThat(result.task(":customCompileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

        // Now enable Error Prone and check that compilation fails.

        // given
        buildFile.appendText(
            """

            customCompileJava.options.errorprone.isEnabled.set(true)
            """.trimIndent()
        )

        // when
        val result2 = buildWithArgsAndFail("customCompileJava")

        // then
        assertThat(result2.task(":customCompileJava")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result2.output).contains(FAILURE_SOURCE_COMPILATION_ERROR)
    }
}
