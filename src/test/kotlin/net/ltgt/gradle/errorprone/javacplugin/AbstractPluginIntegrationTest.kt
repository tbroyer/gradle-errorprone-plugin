package net.ltgt.gradle.errorprone.javacplugin

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import org.gradle.util.TextUtil
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

abstract class AbstractPluginIntegrationTest {
    @JvmField
    @Rule
    val testProjectDir = TemporaryFolder()

    lateinit var settingsFile: File
    lateinit var buildFile: File

    private val testJavaHome = System.getProperty("test.java-home")
    private val testGradleVersion = System.getProperty("test.gradle-version", GradleVersion.current().version)
    private val pluginVersion = System.getProperty("plugin.version")!!

    protected val errorproneVersion = System.getProperty("errorprone.version")!!

    @Before
    fun setupProject() {
        // See https://github.com/gradle/kotlin-dsl/issues/492
        val testRepository = TextUtil.normaliseFileSeparators(File("build/repository").absolutePath)
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

            """.trimIndent())
        }
    }

    protected fun writeSuccessSource() {
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
    }

    protected fun writeFailureSource() {
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
    }

    protected fun buildWithArgs(vararg tasks: String): BuildResult {
        return prepareBuild(*tasks)
            .build()
    }

    protected fun buildWithArgsAndFail(vararg tasks: String): BuildResult {
        return prepareBuild(*tasks)
            .buildAndFail()
    }

    private fun prepareBuild(vararg tasks: String): GradleRunner {
        testJavaHome?.also {
            buildFile.appendText("""

                tasks.withType<JavaCompile>() {
                  options.isFork = true
                  options.forkOptions.javaHome = File(""${'"'}${it.replace("\$", "\${'\$'}")}${'"'}"")
                }
            """.trimIndent())
        }

        return GradleRunner.create()
            .withGradleVersion(testGradleVersion)
            .withProjectDir(testProjectDir.root)
            .withArguments(*tasks)
    }
}
