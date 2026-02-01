package net.ltgt.gradle.errorprone;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Objects.requireNonNull;

import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.util.GradleVersion;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

public class ManualConfigurationIntegrationTest extends BasePluginIntegrationTest {
  @Test
  void inNonJavaProjectWithAppliedPlugin() throws Exception {
    // given
    Files.writeString(
        getBuildFile(),
        // language=kts
        """
        import net.ltgt.gradle.errorprone.*

        plugins {
            id("%s")
            `jvm-ecosystem`
        }

        repositories {
            mavenCentral()
        }
        dependencies {
            errorprone("com.google.errorprone:error_prone_core:%s")
        }
        %s

        val compileJava by tasks.registering(JavaCompile::class) {
            source("src/main/java")
            classpath = files()
            destinationDirectory.set(layout.buildDirectory.dir("classes"))
            sourceCompatibility = "8"
            targetCompatibility = "8"
            options.annotationProcessorPath = annotationProcessor.get()

            options.errorprone {
                enabled.set(true)
                disableAllChecks.set(true)
                error("ArrayEquals")
            }
        }
        """
            .formatted(
                ErrorPronePlugin.PLUGIN_ID,
                errorproneVersion,
                registerResolvableConfiguration("annotationProcessor")));
    writeFailureSource();

    // when
    var result = buildWithArgsAndFail("--info", "compileJava");

    // then
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.FAILED);
    assertThat(result.getOutput()).contains(FAILURE_SOURCE_COMPILATION_ERROR);
  }

  @Test
  void inJavaProject() throws Exception {
    // This is similar to what the me.champeau.gradle.jmh plugin does

    // given
    Files.writeString(
        getBuildFile(),
        // language=kts
        """
        import net.ltgt.gradle.errorprone.*

        plugins {
            java
            id("%s")
        }

        repositories {
            mavenCentral()
        }
        dependencies {
            errorprone("com.google.errorprone:error_prone_core:%s")
        }
        %s

        val customCompileJava by tasks.registering(JavaCompile::class) {
            source("src/main/java")
            classpath = files()
            destinationDirectory.set(layout.buildDirectory.dir("classes/custom"))
            options.annotationProcessorPath = customAnnotationProcessor.get()

            options.errorprone {
                disableAllChecks.set(true)
                error("ArrayEquals")
            }
        }
        """
            .formatted(
                ErrorPronePlugin.PLUGIN_ID,
                errorproneVersion,
                registerResolvableConfiguration("customAnnotationProcessor")));
    writeFailureSource();

    // Error Prone is disabled by default, so compilation should succeed.

    var result = buildWithArgs("customCompileJava");

    // then
    assertThat(requireNonNull(result.task(":customCompileJava")).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);

    // Now enable Error Prone and check that compilation fails.

    // given
    Files.writeString(
        getBuildFile(),
        """

        customCompileJava { options.errorprone.enabled.set(true) }
        """,
        StandardOpenOption.APPEND);

    // when
    result = buildWithArgsAndFail("customCompileJava");

    // then
    assertThat(requireNonNull(result.task(":customCompileJava")).getOutcome())
        .isEqualTo(TaskOutcome.FAILED);
    assertThat(result.getOutput()).contains(FAILURE_SOURCE_COMPILATION_ERROR);
  }

  private @NonNull String registerResolvableConfiguration(String name) {
    // language=kts
    return testGradleVersion.compareTo(GradleVersion.version("8.4")) >= 0
        ? """
          val %1$s = configurations.resolvable("%1$s") {
              extendsFrom(configurations["errorprone"])
          }
          """
            .formatted(name)
        : """
          val %s by configurations.registering {
              isCanBeConsumed = false
              isCanBeResolved = true
              extendsFrom(configurations["errorprone"])
          }
          """
            .formatted(name);
  }
}
