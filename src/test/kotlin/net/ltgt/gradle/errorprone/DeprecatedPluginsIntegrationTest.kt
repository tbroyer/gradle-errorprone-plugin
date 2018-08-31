package net.ltgt.gradle.errorprone

import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Test

@Suppress("DEPRECATION")
class DeprecatedPluginsIntegrationTest : AbstractPluginIntegrationTest() {
    @Test
    fun `errorprone-base displays deprecation warning and is a no-op`() {
        // given
        buildFile.appendText("""
            plugins {
                `java-library`
                id("${ErrorProneBasePlugin.PLUGIN_ID}")
            }

            val compileJava by tasks.getting(JavaCompile::class)
            val checkPlugin by tasks.creating {
                doFirst {
                    check(configurations.findByName("${ErrorPronePlugin.CONFIGURATION_NAME}") == null)
                    check(configurations.findByName("${ErrorPronePlugin.JAVAC_CONFIGURATION_NAME}") == null)
                    check((compileJava.options as ExtensionAware).extensions.findByName("${ErrorProneOptions.NAME}") == null)
                }
            }
            compileJava.finalizedBy(checkPlugin)
        """.trimIndent())
        writeFailureSource()

        // when
        val result = buildWithArgs("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.task(":checkPlugin")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).contains(deprecationMessage(ErrorProneBasePlugin.PLUGIN_ID))
    }

    @Test
    fun `errorprone-javacplugin displays deprecation warning and applies errorprone`() {
        // given
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
                errorproneJavac("com.google.errorprone:javac:$errorproneJavacVersion")
            }
        """.trimIndent())
        writeFailureSource()

        // when
        val result = buildWithArgsAndFail("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains(deprecationMessage(ErrorProneJavacPluginPlugin.PLUGIN_ID))
    }
}
