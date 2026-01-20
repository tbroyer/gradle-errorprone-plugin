package net.ltgt.gradle.errorprone

import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BinaryCompatibilityIntegrationTest : AbstractPluginIntegrationTest() {
    @TempDir
    lateinit var conventionPluginProjectDir: File
    lateinit var conventionPluginBuildFile: File

    @BeforeEach
    fun setup() {
        testProjectDir.resolve("gradle.properties").copyTo(conventionPluginProjectDir.resolve("gradle.properties"))
        buildFile.writeText(
            """
            plugins {
                `java-library`
                id("${ErrorPronePlugin.PLUGIN_ID}")
                id("convention.errorprone")
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                errorprone("com.google.errorprone:error_prone_core:$errorproneVersion")
            }

            tasks.withType<JavaCompile>().configureEach {
                options.compilerArgs.add("-Werror")
            }
            """.trimIndent(),
        )
        conventionPluginProjectDir.resolve("settings.gradle.kts").apply {
            writeText(
                """
                rootProject.name = "convention-plugin"
                """.trimIndent(),
            )
        }
        conventionPluginBuildFile =
            conventionPluginProjectDir.resolve("build.gradle.kts").apply {
                createNewFile()
            }
    }

    private fun buildConventionPlugin() {
        GradleRunner
            .create()
            .withGradleVersion(testGradleVersion.version)
            .withProjectDir(conventionPluginProjectDir)
            .withArguments("assemble")
            .build()
    }

    private fun buildProject(vararg tasks: String) =
        GradleRunner
            .create()
            .withGradleVersion(testGradleVersion.version)
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .apply {
                withPluginClasspath(pluginClasspath + conventionPluginProjectDir.resolve("build/libs/convention-plugin.jar"))
            }.withArguments(*tasks)
            .build()

    @Test
    fun `is binary-compatible with previous versions (kotlin-dsl flavor)`() {
        // given
        conventionPluginBuildFile.writeText(
            """
            plugins {
                `kotlin-dsl`
            }

            repositories {
                gradlePluginPortal()
            }

            dependencies {
                implementation("${ErrorPronePlugin.PLUGIN_ID}:${ErrorPronePlugin.PLUGIN_ID}.gradle.plugin:4.4.0")
            }
            """.trimIndent(),
        )
        conventionPluginProjectDir.resolve("src/main/kotlin").apply { mkdirs() }.resolve("convention.errorprone.gradle.kts").apply {
            writeText(
                """
                import net.ltgt.gradle.errorprone.*

                plugins {
                    id("${ErrorPronePlugin.PLUGIN_ID}")
                }

                tasks.withType<JavaCompile>().configureEach {
                    options.errorprone {
                        isEnabled.set(true)
                        isCompilingTestOnlyCode.set(false)
                    }
                }
                """.trimIndent(),
            )
        }

        buildConventionPlugin()

        testProjectDir.writeSuccessSource()

        // when
        val result = buildProject("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `is binary-compatible with previous versions (java flavor)`() {
        // given
        conventionPluginBuildFile.writeText(
            """
            plugins {
                `java-gradle-plugin`
            }

            repositories {
                gradlePluginPortal()
            }

            dependencies {
                implementation("${ErrorPronePlugin.PLUGIN_ID}:${ErrorPronePlugin.PLUGIN_ID}.gradle.plugin:4.4.0")
            }

            gradlePlugin {
                plugins {
                    create("conventionPlugin") {
                        id = "convention.errorprone"
                        implementationClass = "convention.ConventionErrorPronePlugin"
                    }
                }
            }
            """.trimIndent(),
        )
        conventionPluginProjectDir.resolve("src/main/java/convention").apply { mkdirs() }.resolve("ConventionErrorPronePlugin.java").apply {
            writeText(
                """
                package convention;

                import net.ltgt.gradle.errorprone.CheckSeverity;
                import net.ltgt.gradle.errorprone.ErrorProneOptions;
                import net.ltgt.gradle.errorprone.ErrorPronePlugin;
                import org.gradle.api.Plugin;
                import org.gradle.api.Project;
                import org.gradle.api.plugins.ExtensionAware;
                import org.gradle.api.tasks.compile.JavaCompile;

                public class ConventionErrorPronePlugin implements Plugin<Project> {
                  @Override
                  public void apply(Project project) {
                    project.getPluginManager().apply(ErrorPronePlugin.PLUGIN_ID);

                    project
                        .getTasks()
                        .withType(JavaCompile.class)
                        .configureEach(
                            task -> {
                                ErrorProneOptions errorproneOptions =
                                    ((ExtensionAware) task.getOptions())
                                        .getExtensions()
                                        .getByType(ErrorProneOptions.class);
                                errorproneOptions.getEnabled().set(true);
                                errorproneOptions.getCompilingTestOnlyCode().set(false);
                            });
                  }
                }
                """.trimIndent(),
            )
        }

        buildConventionPlugin()

        testProjectDir.writeSuccessSource()

        // when
        val result = buildProject("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }
}
