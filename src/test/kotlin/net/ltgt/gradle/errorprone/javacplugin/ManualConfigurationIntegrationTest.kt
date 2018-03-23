package net.ltgt.gradle.errorprone.javacplugin

import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test

class ManualConfigurationIntegrationTest : AbstractPluginIntegrationTest() {

    @Test
    fun `in non-java project with applied plugin`() {
        // given
        buildFile.appendText("""
            plugins {
                id("${ErrorProneJavacPluginPlugin.PLUGIN_ID}")
            }

            repositories {
                jcenter()
            }
            dependencies {
                errorprone("com.google.errorprone:error_prone_core:$errorproneVersion")
            }

            val compileJava by tasks.creating(JavaCompile::class) {
                source("src/main/java")
                classpath = files()
                destinationDir = file("${'$'}buildDir/classes")
                sourceCompatibility = "8"
                targetCompatibility = "8"
                options.annotationProcessorPath = configurations["errorprone"]

                ErrorProneJavacPlugin.apply(options)
                options.errorprone {
                    disableAllChecks = true
                    check("ArrayEquals", CheckSeverity.ERROR)
                }
            }
        """.trimIndent())
        writeFailureSource()

        // when
        val result = buildWithArgsAndFail("--info", "compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains("Failure.java:6: error: [ArrayEquals]")
    }

    @Test
    fun `in java project with non-applied plugin`() {
        // given
        buildFile.appendText("""
            plugins {
                java
                id("${ErrorProneJavacPluginPlugin.PLUGIN_ID}") apply false
            }

            repositories {
                jcenter()
            }
            dependencies {
                annotationProcessor("com.google.errorprone:error_prone_core:$errorproneVersion")
            }

            val compileJava by tasks.getting(JavaCompile::class) {
                ErrorProneJavacPlugin.apply(options)
                options.errorprone {
                    disableAllChecks = true
                    check("ArrayEquals", CheckSeverity.ERROR)
                }
            }
        """.trimIndent())
        writeFailureSource()

        // when
        val result = buildWithArgsAndFail("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains("Failure.java:6: error: [ArrayEquals]")
    }
}
