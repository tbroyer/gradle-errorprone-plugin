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
                        disableAllChecks.set(false)
                        disableAllWarnings.set(false)
                        allErrorsAsWarnings.set(false)
                        allSuggestionsAsWarnings.set(false)
                        allDisabledChecksAsWarnings.set(false)
                        disableWarningsInGeneratedCode.set(false)
                        ignoreUnknownCheckNames.set(false)
                        ignoreSuppressionAnnotations.set(false)
                        isCompilingTestOnlyCode.set(false)
                        excludedPaths.set("should.not.match.anything")

                        check("Foo" to CheckSeverity.ERROR, "Bar" to CheckSeverity.DEFAULT)
                        check("Foo", CheckSeverity.WARN)
                        check("Bar", provider { CheckSeverity.OFF })
                        enable("Foo", "Bar")
                        disable("Foo", "Bar")
                        warn("Foo", "Bar")
                        error("Foo", "Bar")
                        checks.empty()

                        option("Foo:Bar")
                        option("Foo:Bar", false)
                        option("Foo:Bar", "baz")
                        option("Foo:Bar", provider { "baz" })
                        checkOptions.empty()

                        errorproneArgs.empty()
                        errorproneArgumentProviders.clear()
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
                                errorproneOptions.getDisableAllChecks().set(false);
                                errorproneOptions.getDisableAllWarnings().set(false);
                                errorproneOptions.getAllErrorsAsWarnings().set(false);
                                errorproneOptions.getAllSuggestionsAsWarnings().set(false);
                                errorproneOptions.getAllDisabledChecksAsWarnings().set(false);
                                errorproneOptions.getDisableWarningsInGeneratedCode().set(false);
                                errorproneOptions.getIgnoreUnknownCheckNames().set(false);
                                errorproneOptions.getIgnoreSuppressionAnnotations().set(false);
                                errorproneOptions.getCompilingTestOnlyCode().set(false);
                                errorproneOptions.getExcludedPaths().set("should.not.match.anything");

                                errorproneOptions.check(new kotlin.Pair<>("Foo", CheckSeverity.ERROR), new kotlin.Pair<>("Bar", CheckSeverity.DEFAULT));
                                errorproneOptions.check("Foo", CheckSeverity.WARN);
                                errorproneOptions.check("Bar", project.provider(() -> CheckSeverity.OFF));
                                errorproneOptions.enable("Foo", "Bar");
                                errorproneOptions.disable("Foo", "Bar");
                                errorproneOptions.warn("Foo", "Bar");
                                errorproneOptions.error("Foo", "Bar");
                                errorproneOptions.getChecks().empty();

                                errorproneOptions.option("Foo:Bar");
                                errorproneOptions.option("Foo:Bar", false);
                                errorproneOptions.option("Foo:Bar", "baz");
                                errorproneOptions.option("Foo:Bar", project.provider(() -> "baz"));
                                errorproneOptions.getCheckOptions().empty();

                                errorproneOptions.getErrorproneArgs().empty();
                                errorproneOptions.getErrorproneArgumentProviders().clear();
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
