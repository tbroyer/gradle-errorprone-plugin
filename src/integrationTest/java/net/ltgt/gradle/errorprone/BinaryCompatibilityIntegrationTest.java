package net.ltgt.gradle.errorprone;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Objects.requireNonNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BinaryCompatibilityIntegrationTest extends BasePluginIntegrationTest {
  @TempDir Path conventionPluginProjectDir;
  Path conventionPluginBuildFile;

  @BeforeEach
  void setup() throws Exception {
    Files.copy(
        projectDir.resolve("gradle.properties"),
        conventionPluginProjectDir.resolve("gradle.properties"));
    Files.writeString(
        getBuildFile(),
        // language=kts
        """
        plugins {
            `java-library`
            id("%s")
            id("convention.errorprone")
        }

        repositories {
            mavenCentral()
        }

        dependencies {
            errorprone("com.google.errorprone:error_prone_core:%s")
        }

        tasks.withType<JavaCompile>().configureEach {
            options.compilerArgs.add("-Werror")
        }
        """
            .formatted(ErrorPronePlugin.PLUGIN_ID, errorproneVersion));
    Files.writeString(
        conventionPluginProjectDir.resolve("settings.gradle.kts"),
        """
        rootProject.name = "convention-plugin"
        """);
    conventionPluginBuildFile =
        Files.createFile(conventionPluginProjectDir.resolve("build.gradle.kts"));
  }

  private void buildConventionPlugin() {
    GradleRunner.create()
        .withGradleVersion(testGradleVersion.getVersion())
        .withProjectDir(conventionPluginProjectDir.toFile())
        .withArguments("assemble")
        .build();
  }

  private BuildResult buildProject(String... args) {
    var runner =
        GradleRunner.create()
            .withGradleVersion(testGradleVersion.getVersion())
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath();
    return runner
        .withPluginClasspath(
            Stream.concat(
                    runner.getPluginClasspath().stream(),
                    Stream.of(
                        conventionPluginProjectDir
                            .resolve("build/libs/convention-plugin.jar")
                            .toFile()))
                .toList())
        .withArguments(args)
        .build();
  }

  @Test
  void isBinaryCompatibleWithPreviousVersions_kotlinDslFlavor() throws Exception {
    // given
    Files.writeString(
        conventionPluginBuildFile,
        // language=kts
        """
        plugins {
            `kotlin-dsl`
        }

        repositories {
            gradlePluginPortal()
        }

        dependencies {
            implementation("%1$s:%1$s.gradle.plugin:4.4.0")
        }
        """
            .formatted(ErrorPronePlugin.PLUGIN_ID));
    Files.writeString(
        Files.createDirectories(conventionPluginProjectDir.resolve("src/main/kotlin"))
            .resolve("convention.errorprone.gradle.kts"),
        // language=kts
        """
        import net.ltgt.gradle.errorprone.*

        plugins {
            id("%s")
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
        """
            .formatted(ErrorPronePlugin.PLUGIN_ID));

    buildConventionPlugin();

    writeSuccessSource();

    // when
    var result = buildProject("compileJava");

    // then
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
  }

  @Test
  void isBinaryCompatibleWithPreviousVersions_javaFlavor() throws Exception {
    // given
    Files.writeString(
        conventionPluginBuildFile,
        // language=kts
        """
        plugins {
            `java-gradle-plugin`
        }

        repositories {
            gradlePluginPortal()
        }

        dependencies {
            implementation("%1$s:%1$s.gradle.plugin:4.4.0")
        }

        gradlePlugin {
            plugins {
                create("conventionPlugin") {
                    id = "convention.errorprone"
                    implementationClass = "convention.ConventionErrorPronePlugin"
                }
            }
        }
        """
            .formatted(ErrorPronePlugin.PLUGIN_ID));
    Files.writeString(
        Files.createDirectories(conventionPluginProjectDir.resolve("src/main/java/convention"))
            .resolve("ConventionErrorPronePlugin.java"),
        // language=java
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
        """);

    buildConventionPlugin();

    writeSuccessSource();

    // when
    var result = buildProject("compileJava");

    // then
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
  }
}
