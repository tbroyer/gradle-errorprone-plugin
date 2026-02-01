package net.ltgt.gradle.errorprone;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static java.util.Objects.requireNonNull;

import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import org.gradle.api.JavaVersion;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StrongEncapsulationIntegrationTest extends BasePluginIntegrationTest {
  private static final String FORKED = "%1$sFork: true%1$s".formatted(System.lineSeparator());
  private static final String NOT_FORKED = "%1$sFork: false%1$s".formatted(System.lineSeparator());
  private static final String JVM_ARG = "%sJVM Arg: ".formatted(System.lineSeparator());
  private static final String JVM_ARGS_STRONG_ENCAPSULATION =
      JVM_ARG + String.join(JVM_ARG, ErrorPronePlugin.JVM_ARGS_STRONG_ENCAPSULATION);

  private static String jvmArg(String argPrefix) {
    return JVM_ARG + argPrefix;
  }

  @BeforeEach
  void setup() throws Exception {
    Files.writeString(
        getBuildFile(),
        // language=kts
        """
        import net.ltgt.gradle.errorprone.*

        plugins {
            `java-library`
            id("%s")
        }
        repositories {
            mavenCentral()
        }
        dependencies {
            errorprone("com.google.errorprone:error_prone_core:%s")
        }

        val compileJava: JavaCompile by tasks
        val displayCompileJavaOptions by tasks.registering {
            doFirst {
                println("Fork: ${compileJava.options.isFork}")
                compileJava.options.forkOptions.allJvmArgs.forEach { arg ->
                    println("JVM Arg: $arg")
                }
            }
        }
        compileJava.finalizedBy(displayCompileJavaOptions)
        compileJava.options.forkOptions.jvmArgs!!.add("-XshowSettings")
        """
            .formatted(ErrorPronePlugin.PLUGIN_ID, errorproneVersion));
    writeSuccessSource();
  }

  @Test
  void doesNotConfigureForkingInJavaBefore16() throws Exception {
    assume()
        .withMessage("isJava16Compatible")
        .that(testJavaVersion)
        .isLessThan(JavaVersion.VERSION_16);

    // when
    var result = buildWithArgs("compileJava");
    // then
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.getOutput()).contains(NOT_FORKED);
    // Check that the configured jvm arg is preserved
    assertThat(result.getOutput()).contains(jvmArg("-XshowSettings"));

    // Test a forked task

    // given
    Files.writeString(
        getBuildFile(),
        // language=kts
        """

        compileJava.options.isFork = true
        """,
        StandardOpenOption.APPEND);

    // when
    result = buildWithArgs("compileJava");
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.getOutput()).contains(FORKED);
    assertThat(result.getOutput()).contains(JVM_ARGS_STRONG_ENCAPSULATION);
    // Check that the configured jvm arg is preserved
    assertThat(result.getOutput()).contains(jvmArg("-XshowSettings"));
  }

  @Test
  void configureForkingInJava16PlusVM() throws Exception {
    assume()
        .withMessage("isJava16Compatible")
        .that(testJavaVersion)
        .isAtLeast(JavaVersion.VERSION_16);

    // when
    var result = buildWithArgs("compileJava");
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.getOutput()).contains(FORKED);
    assertThat(result.getOutput()).contains(JVM_ARGS_STRONG_ENCAPSULATION);
    // Check that the configured jvm arg is preserved
    assertThat(result.getOutput()).contains(jvmArg("-XshowSettings"));

    // check that it doesn't mess with task avoidance

    // when
    result = buildWithArgs("compileJava");
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.UP_TO_DATE);
  }

  @Test
  void doesNotConfigureForkingIfErrorProneIsDisabled() throws Exception {
    assume()
        .withMessage("isJava16Compatible")
        .that(testJavaVersion)
        .isAtLeast(JavaVersion.VERSION_16);

    // given
    Files.writeString(
        getBuildFile(),
        // language=kts
        """

        compileJava.options.errorprone.enabled.set(false);
        """,
        StandardOpenOption.APPEND);

    // when
    var result = buildWithArgs("compileJava");

    // then
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.getOutput()).contains(NOT_FORKED);
    // Check that the configured jvm arg is preserved
    assertThat(result.getOutput()).contains(jvmArg("-XshowSettings"));
  }
}
