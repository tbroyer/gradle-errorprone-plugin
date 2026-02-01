package net.ltgt.gradle.errorprone;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Objects.requireNonNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GroovyDslIntegrationTest extends BasePluginIntegrationTest {
  @Override
  protected Path getSettingsFile() {
    return projectDir.resolve("settings.gradle");
  }

  @Override
  protected Path getBuildFile() {
    return projectDir.resolve("build.gradle");
  }

  @BeforeEach
  void setup() throws Exception {
    Files.writeString(
        getBuildFile(),
        // language=groovy
        """
        plugins {
            id("java-library")
            id("%s")
        }
        repositories {
            mavenCentral()
        }
        dependencies {
            errorprone "com.google.errorprone:error_prone_core:%s"
        }
        """
            .formatted(ErrorPronePlugin.PLUGIN_ID, errorproneVersion));
  }

  @Test
  void canDisableErrorProne() throws Exception {
    Files.writeString(
        getBuildFile(),
        // language=groovy
        """

        tasks.withType(JavaCompile).configureEach {
            options.errorprone.enabled = false
        }
        """,
        StandardOpenOption.APPEND);
    writeFailureSource();

    // when
    var result = buildWithArgs("compileJava");

    // then
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
  }

  @Test
  void canConfigureErrorProne() throws Exception {
    // given
    Files.writeString(
        getBuildFile(),
        // language=groovy
        """

        import net.ltgt.gradle.errorprone.CheckSeverity
        import kotlin.Pair

        tasks.withType(JavaCompile).configureEach {
            options.errorprone {
                // configure with the default values, we only want to check the DSL, not the effects
                enabled = true
                disableAllChecks = false
                disableAllWarnings = false
                allErrorsAsWarnings = false
                allSuggestionsAsWarnings = false
                allDisabledChecksAsWarnings = false
                disableWarningsInGeneratedCode = false
                ignoreUnknownCheckNames = false
                ignoreSuppressionAnnotations = false
                compilingTestOnlyCode = false
                excludedPaths = "should.not.match.anything"

                check new Pair("Foo", CheckSeverity.ERROR), new Pair("Bar", CheckSeverity.DEFAULT)
                check "Foo", CheckSeverity.WARN
                check "Bar", provider { CheckSeverity.OFF }
                enable "Foo", "Bar"
                disable "Foo", "Bar"
                warn "Foo", "Bar"
                error "Foo", "Bar"
                checks.empty()

                option "Foo:Bar"
                option "Foo:Bar", false
                option "Foo:Bar", "baz"
                option "Foo:Bar", provider { "baz" }
                checkOptions.empty()

                errorproneArgs.empty()
                errorproneArgumentProviders.clear()
            }
        }
        """,
        StandardOpenOption.APPEND);
    writeSuccessSource();

    // when
    var result = buildWithArgs("compileJava");

    // then
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
  }
}
