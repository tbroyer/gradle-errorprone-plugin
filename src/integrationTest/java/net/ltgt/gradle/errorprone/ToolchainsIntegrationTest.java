package net.ltgt.gradle.errorprone;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static java.util.Objects.requireNonNull;

import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import org.gradle.api.JavaVersion;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.util.GradleVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ToolchainsIntegrationTest extends BasePluginIntegrationTest {
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
        projectDir.resolve("gradle.properties"),
        // language=properties
        """

        org.gradle.java.installations.auto-download=false
        """,
        StandardOpenOption.APPEND);

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

        tasks {
            val alwaysFail by registering {
                doFirst {
                    error("Forced failure")
                }
            }
            val displayCompileJavaOptions by registering {
                finalizedBy(alwaysFail)
                doFirst {
                    println("ErrorProne: ${if (compileJava.get().options.errorprone.enabled.getOrElse(false)) "enabled" else "disabled"}")
                    println("Fork: ${compileJava.get().options.isFork}")
                    compileJava.get().options.forkOptions.allJvmArgs.forEach { arg ->
                        println("JVM Arg: $arg")
                    }
                }
            }
            compileJava {
                finalizedBy(displayCompileJavaOptions)
                options.forkOptions.jvmArgs!!.add("-XshowSettings")
            }
        }
        """
            .formatted(ErrorPronePlugin.PLUGIN_ID));
    writeSuccessSource();
  }

  private void assumeToolchainAvailable(BuildResult result) {
    assume()
        .withMessage("No compatible toolchains found")
        .that(
            result.task("alwaysFail") == null
                &&
                // XXX: some Gradle versions use "request filter", others (7.6+) use "request
                // specification"
                result.getOutput().contains("No compatible toolchains found for request "))
        .isFalse();
  }

  @Test
  void failsWhenConfiguredToolchainIsTooOld() throws Exception {
    // given

    // Fake a JDK 8 toolchain, the task should never actually run anyway.
    // We cannot even use a real toolchain and rely on auto-download=false as that
    // would fail too early (when computing task inputs, so our doFirst won't run)
    Files.writeString(
        getBuildFile(),
        // language=kts
        """

        tasks.compileJava {
            javaCompiler.set(object : JavaCompiler {
                override fun getExecutablePath(): RegularFile = TODO()
                override fun getMetadata(): JavaInstallationMetadata = object : JavaInstallationMetadata {
                    override fun getLanguageVersion(): JavaLanguageVersion = JavaLanguageVersion.of(8)
                    override fun getInstallationPath(): Directory = TODO()
                    override fun getVendor(): String = TODO()
                    override fun getJavaRuntimeVersion(): String = TODO()
                    override fun getJvmVersion(): String = TODO()
                   %s override fun isCurrentJvm() = false
                }
            })
        }
        """
            .formatted(testGradleVersion.compareTo(GradleVersion.version("8.0")) >= 0 ? "" : " //"),
        StandardOpenOption.APPEND);

    // First test that it's disabled by default

    // when
    var result = buildWithArgsAndFail("displayCompileJavaOptions");
    // then
    assertThat(requireNonNull(result.task(":displayCompileJavaOptions")).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.getOutput()).contains("ErrorProne: disabled");

    // Then test that it fails if we force-enable it

    Files.writeString(
        getBuildFile(),
        // language=kts
        """

        tasks.compileJava { options.errorprone.enabled.set(true) }
        """,
        StandardOpenOption.APPEND);

    // when
    result = buildWithArgsAndFail("compileJava");
    // then
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.FAILED);
    assertThat(result.getOutput()).contains(ErrorPronePlugin.TOO_OLD_TOOLCHAIN_ERROR_MESSAGE);
  }

  @Test
  void doesNotForceForkingInJavaBefore16() throws Exception {
    // given
    Files.writeString(
        getBuildFile(),
        // language=kts
        """

        java {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(11))
            }
        }
        dependencies {
            errorprone("com.google.errorprone:error_prone_core:%s")
        }
        """
            .formatted(MAX_JDK11_COMPATIBLE_ERRORPRONE_VERSION),
        StandardOpenOption.APPEND);

    // when
    var result = buildWithArgsAndFail("compileJava");
    assumeToolchainAvailable(result);
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.getOutput()).contains(NOT_FORKED);
    assertThat(result.getOutput()).contains(JVM_ARGS_STRONG_ENCAPSULATION);
    // Check that the configured jvm arg is preserved
    assertThat(result.getOutput()).contains(jvmArg("-XshowSettings"));

    // Test a forked task

    // given
    Files.writeString(
        getBuildFile(),
        // language=kts
        """

        tasks.compileJava { options.isFork = true }
        """,
        StandardOpenOption.APPEND);

    // when
    result = buildWithArgsAndFail("compileJava");
    assumeToolchainAvailable(result);
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.getOutput()).contains(FORKED);
    assertThat(result.getOutput()).contains(JVM_ARGS_STRONG_ENCAPSULATION);
    // Check that the configured jvm arg is preserved
    assertThat(result.getOutput()).contains(jvmArg("-XshowSettings"));
  }

  @Test
  void configureForkingInJava16PlusVMUnlessImplicitlyForkedByIncompatibleToolchain()
      throws Exception {
    // https://docs.gradle.org/current/userguide/compatibility.html#java_runtime
    assume().that(testGradleVersion).isAtLeast(GradleVersion.version("7.3"));

    // given
    Files.writeString(
        getBuildFile(),
        // language=kts
        """

        java {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(17))
            }
        }
        dependencies {
            errorprone("com.google.errorprone:error_prone_core:%s")
        }
        """
            .formatted(MAX_JDK17_COMPATIBLE_ERRORPRONE_VERSION),
        StandardOpenOption.APPEND);

    // when
    var result = buildWithArgsAndFail("compileJava");
    assumeToolchainAvailable(result);
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.getOutput())
        .contains(testJavaVersion == JavaVersion.VERSION_17 ? FORKED : NOT_FORKED);
    assertThat(result.getOutput()).contains(JVM_ARGS_STRONG_ENCAPSULATION);
    // Check that the configured jvm arg is preserved
    assertThat(result.getOutput()).contains(jvmArg("-XshowSettings"));

    // check that it doesn't mess with task avoidance

    // when
    result = buildWithArgsAndFail("compileJava");
    assumeToolchainAvailable(result);
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.UP_TO_DATE);
  }

  @Test
  void doesNotConfigureForkingInJava16PlusVMIfCurrentJVMHasAppropriateJvmArgs() throws Exception {
    assume()
        .withMessage("isJava16Compatible")
        .that(testJavaVersion)
        .isAtLeast(JavaVersion.VERSION_16);

    Files.writeString(
        projectDir.resolve("gradle.properties"),
        // language=properties
        """

        org.gradle.jvmargs=-Xmx512m "-XX:MaxMetaspaceSize=384m" %s
        """
            .formatted(String.join(" ", ErrorPronePlugin.JVM_ARGS_STRONG_ENCAPSULATION)),
        StandardOpenOption.APPEND);

    // given
    Files.writeString(
        getBuildFile(),
        // language=kts
        """

        java {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(%s))
            }
        }
        dependencies {
            errorprone("com.google.errorprone:error_prone_core:%s")
        }
        """
            .formatted(testJavaVersion.getMajorVersion(), errorproneVersion),
        StandardOpenOption.APPEND);

    // when
    var result = buildWithArgsAndFail("compileJava");
    // then
    assumeToolchainAvailable(result);
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.getOutput()).contains(NOT_FORKED);
    assertThat(result.getOutput()).contains(JVM_ARGS_STRONG_ENCAPSULATION);
    // Check that the configured jvm arg is preserved
    assertThat(result.getOutput()).contains(jvmArg("-XshowSettings"));

    // check that it doesn't mess with task avoidance

    // when
    result = buildWithArgsAndFail("compileJava");
    // then
    assumeToolchainAvailable(result);
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.UP_TO_DATE);
  }

  @Test
  void doesNotConfigureForkingWithJdk19PlusIfErrorProneIsDisabled() throws Exception {
    // https://docs.gradle.org/current/userguide/compatibility.html#java_runtime
    assume().that(testGradleVersion).isAtLeast(GradleVersion.version("7.3"));

    // given
    Files.writeString(
        getBuildFile(),
        // language=kts
        """

        java {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(17))
            }
        }
        dependencies {
            errorprone("com.google.errorprone:error_prone_core:%s")
        }

        tasks.compileJava { options.errorprone.enabled.set(false) }
        """
            .formatted(MAX_JDK17_COMPATIBLE_ERRORPRONE_VERSION),
        StandardOpenOption.APPEND);

    // when
    var result = buildWithArgsAndFail("compileJava");

    // then
    assumeToolchainAvailable(result);
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.getOutput()).contains(NOT_FORKED);
    assertThat(result.getOutput()).doesNotContain(JVM_ARGS_STRONG_ENCAPSULATION);
    // Check that the configured jvm arg is preserved
    assertThat(result.getOutput()).contains(jvmArg("-XshowSettings"));
  }
}
