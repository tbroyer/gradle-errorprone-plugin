package net.ltgt.gradle.errorprone.javacplugin

import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import org.gradle.util.TextUtil.normaliseFileSeparators
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ErrorProneJavacPluginPluginIntegrationTest {
    @JvmField
    @Rule
    val testProjectDir = TemporaryFolder()

    lateinit var settingsFile: File
    lateinit var buildFile: File

    private val testJavaHome = System.getProperty("test.java-home")
    private val testGradleVersion = System.getProperty("test.gradle-version", GradleVersion.current().version)
    private val pluginVersion = System.getProperty("plugin.version")
    private val errorproneVersion = System.getProperty("errorprone.version")

    @Before
    fun setup() {
        // See https://github.com/gradle/kotlin-dsl/issues/492
        val testRepository = normaliseFileSeparators(File("build/repository").absolutePath)
        settingsFile = testProjectDir.newFile("settings.gradle.kts").apply {
            writeText("""
                pluginManagement {
                    repositories {
                        maven { url = uri("$testRepository") }
                    }
                    resolutionStrategy {
                        eachPlugin {
                            if (requested.id.id == "${ErrorProneJavacPluginPlugin.PLUGIN_ID}") {
                                useVersion("$pluginVersion")
                            }
                        }
                    }
                }
            """.trimIndent())
        }
        buildFile = testProjectDir.newFile("build.gradle.kts").apply {
            writeText("""
                import net.ltgt.gradle.errorprone.javacplugin.*

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
            testJavaHome?.also {
                appendText("""

                    tasks.withType<JavaCompile>() {
                      options.isFork = true
                      options.forkOptions.javaHome = File(""${'"'}${it.replace("\$", "\${'\$'}")}${'"'}"")
                    }
                """.trimIndent())
            }
        }
    }

    @Test
    fun `compilation succeeds`() {
        // given
        File(testProjectDir.newFolder("src", "main", "java", "test"), "Success.java").apply {
            createNewFile()
            writeText("""
                package test;

                public class Success {
                  // See http://errorprone.info/bugpattern/ArrayEquals
                  @SuppressWarnings("ArrayEquals")
                  public boolean arrayEquals(int[] a, int[] b) {
                    return a.equals(b);
                  }
                }
            """.trimIndent())
        }

        // when
        val result = GradleRunner.create()
            .withGradleVersion(testGradleVersion)
            .withProjectDir(testProjectDir.root)
            .withArguments("compileJava")
            .build()

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `compilation fails`() {
        // given
        File(testProjectDir.newFolder("src", "main", "java", "test"), "Failure.java").apply {
            createNewFile()
            writeText("""
                package test;

                public class Failure {
                  // See http://errorprone.info/bugpattern/ArrayEquals
                  public boolean arrayEquals(int[] a, int[] b) {
                    return a.equals(b);
                  }
                }
            """.trimIndent())
        }

        // when
        val result = GradleRunner.create()
            .withGradleVersion(testGradleVersion)
            .withProjectDir(testProjectDir.root)
            .withArguments("compileJava")
            .buildAndFail()

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains("Failure.java:6: error: [ArrayEquals]")
    }

    @Test
    fun `can configure errorprone`() {
        // given
        buildFile.appendText("""

            tasks.withType<JavaCompile>() {
                options.errorproneOptions {
                    errorproneArgs.add("-Xep:ArrayEquals:OFF")
                }
            }
        """.trimIndent())
        File(testProjectDir.newFolder("src", "main", "java", "test"), "Failure.java").apply {
            createNewFile()
            writeText("""
                package test;

                public class Failure {
                  // See http://errorprone.info/bugpattern/ArrayEquals
                  public boolean arrayEquals(int[] a, int[] b) {
                    return a.equals(b);
                  }
                }
            """.trimIndent())
        }

        // when
        val result = GradleRunner.create()
            .withGradleVersion(testGradleVersion)
            .withProjectDir(testProjectDir.root)
            .withArguments("compileJava")
            .build()

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }
}
