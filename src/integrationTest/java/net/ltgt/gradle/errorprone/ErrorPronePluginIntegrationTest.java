package net.ltgt.gradle.errorprone;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ErrorPronePluginIntegrationTest extends BasePluginIntegrationTest {

  @BeforeEach
  void setup() throws IOException {
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
        """
            .formatted(ErrorPronePlugin.PLUGIN_ID, errorproneVersion));
  }

  @Test
  void compilationSucceeds() throws Exception {
    // given
    writeSuccessSource();

    // when
    var result = buildWithArgs("compileJava");

    // then
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
  }

  @Test
  void compilationFails() throws Exception {
    // given
    writeFailureSource();

    // when
    var result = buildWithArgsAndFail("compileJava");

    // then
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.FAILED);
    assertThat(result.getOutput()).contains(FAILURE_SOURCE_COMPILATION_ERROR);
  }

  @Test
  void canConfigureErrorProne() throws Exception {
    // given
    Files.writeString(
        getBuildFile(),
        //  language=kts
        """

        tasks.withType<JavaCompile>().configureEach {
            options.errorprone {
                disable("ArrayEquals")
            }
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
  void canDisableErrorProne() throws Exception {
    // given
    Files.writeString(
        getBuildFile(),
        // language=kts
        """

        tasks.withType<JavaCompile>().configureEach {
          options.errorprone.enabled.set(false)
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
  void configurationNonRegressionTest() throws Exception {
    // given
    Files.writeString(
        getBuildFile(),
        // language=kts
        """

            tasks.withType<JavaCompile>().configureEach {
                options.errorprone {
                    enabled.set(true)
                    disableAllChecks.set(false)
                    disableAllWarnings.set(false)
                    allErrorsAsWarnings.set(false)
                    allSuggestionsAsWarnings.set(false)
                    allDisabledChecksAsWarnings.set(false)
                    disableWarningsInGeneratedCode.set(false)
                    ignoreUnknownCheckNames.set(false)
                    ignoreSuppressionAnnotations.set(false)
                    compilingTestOnlyCode.set(false)
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
        """,
        StandardOpenOption.APPEND);
    writeSuccessSource();

    // when
    var result = buildWithArgs("compileJava");

    // then
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
  }

  @Test
  void doesNotMessWithTaskAvoidance() throws Exception {
    // given
    Files.writeString(
        getBuildFile(),
        // language=kts
        """

            tasks.withType<JavaCompile>().configureEach {
                options.errorprone.enabled.set(
                    providers.gradleProperty("errorprone-enabled").isPresent())
                options.errorprone.check("ArrayEquals",
                    providers.gradleProperty("errorprone-check-enabled").map { CheckSeverity.DEFAULT }.orElse(CheckSeverity.OFF))
            }
        """,
        StandardOpenOption.APPEND);
    writeFailureSource();

    // when
    var result = buildWithArgs("compileJava");
    // then
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);

    // when
    result = buildWithArgs("compileJava");
    // then
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.UP_TO_DATE);

    // when
    result = buildWithArgs("compileJava", "-Perrorprone-enabled=true");
    // then
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);

    // when
    result = buildWithArgs("compileJava", "-Perrorprone-enabled=true");
    // then
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.UP_TO_DATE);

    // when
    result =
        buildWithArgsAndFail(
            "compileJava", "-Perrorprone-enabled=true", "-Perrorprone-check-enabled");
    // then
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.FAILED);
    assertThat(result.getOutput()).contains(FAILURE_SOURCE_COMPILATION_ERROR);
  }

  @Test
  void withCustomCheck() throws Exception {
    // given
    Files.writeString(
        getSettingsFile(),
        // language=kts
        """

        include(":customCheck")
        """,
        StandardOpenOption.APPEND);
    var customCheckProjectDir = Files.createDirectory(projectDir.resolve("customCheck"));
    var customCheckBuildFile = customCheckProjectDir.resolve("build.gradle.kts");
    Files.writeString(
        customCheckBuildFile,
        // language=kts
        """
        plugins {
            java
        }
        repositories {
            mavenCentral()
        }
        dependencies {
            compileOnly("com.google.errorprone:error_prone_check_api:%s")
        }
        if (JavaVersion.current().isJava9Compatible) {
            tasks {
                compileJava {
                    options.compilerArgs.addAll(listOf(
                        "--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
                        "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
                        "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
                        ))
                }
            }
        }
        """
            .formatted(errorproneVersion));
    Files.writeString(
        Files.createDirectories(
                customCheckProjectDir.resolve("src/main/resources/META-INF/services"))
            .resolve("com.google.errorprone.bugpatterns.BugChecker"),
        "com.google.errorprone.sample.MyCustomCheck");
    try (var customCheckSource =
        requireNonNull(
            getClass().getResourceAsStream("/com/google/errorprone/sample/MyCustomCheck.java"))) {
      Files.copy(
          customCheckSource,
          Files.createDirectories(
                  customCheckProjectDir.resolve("src/main/java/com/google/errorprone/sample"))
              .resolve("MyCustomCheck.java"));
    }

    Files.writeString(
        getBuildFile(),
        // language=kts
        """

        dependencies {
            errorprone(project(":customCheck"))
        }
        tasks.withType<JavaCompile>().configureEach {
            options.errorprone.error("MyCustomCheck")
        }
        """,
        StandardOpenOption.APPEND);
    try (var helloSource =
        requireNonNull(
            getClass().getResourceAsStream("/com/google/errorprone/sample/Hello.java"))) {
      Files.copy(
          helloSource,
          Files.createDirectories(projectDir.resolve("src/main/java/com/google/errorprone/sample"))
              .resolve("Hello.java"));
    }

    // when
    var result = buildWithArgsAndFail("compileJava");

    // then
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.FAILED);
    assertThat(result.getOutput())
        .contains("[MyCustomCheck] String formatting inside print method");
  }

  @Test
  void isConfigurationCacheFriendly() throws Exception {
    // given
    // Use a failing check to make sure that the configuration is properly persisted/reloaded
    Files.writeString(
        getBuildFile(),
        """

        tasks.withType<JavaCompile>().configureEach {
            options.errorprone {
                disable("ArrayEquals")
            }
        }
        """,
        StandardOpenOption.APPEND);
    writeFailureSource();

    // Prime the configuration cache
    buildWithArgs("--configuration-cache", "compileJava");

    // when
    var result = buildWithArgs("--configuration-cache", "--rerun-tasks", "--debug", "compileJava");

    // then
    assertThat(result.getOutput()).contains("Reusing configuration cache.");
    // Check that the second run indeed used ErrorProne.
    // As it didn't fail, it means the rest of the configuration was properly persisted/reloaded.
    assertThat(result.getOutput()).contains("-Xplugin:ErrorProne");
  }

  // Inspired by the tests added in Error Prone's https://github.com/google/error-prone/pull/4618
  @Test
  @DisplayName("should-stop ifError")
  void shouldStopIfError() throws Exception {
    // given
    Files.writeString(
        getSettingsFile(),
        """

        include(":customCheck")
        """,
        StandardOpenOption.APPEND);
    var customCheckProjectDir = Files.createDirectory(projectDir.resolve("customCheck"));
    var customCheckBuildFile = customCheckProjectDir.resolve("build.gradle.kts");
    Files.writeString(
        customCheckBuildFile,
        // language=kts
        """
        plugins {
            java
        }
        repositories {
            mavenCentral()
        }
        dependencies {
            compileOnly("com.google.errorprone:error_prone_check_api:%s")
        }
        if (JavaVersion.current().isJava9Compatible) {
            tasks {
                compileJava {
                    options.compilerArgs.addAll(listOf(
                        "--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
                        "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
                        "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
                        ))
                }
            }
        }
        """
            .formatted(errorproneVersion));
    Files.writeString(
        Files.createDirectories(
                customCheckProjectDir.resolve("src/main/resources/META-INF/services"))
            .resolve("com.google.errorprone.bugpatterns.BugChecker"),
        """
            com.google.errorprone.sample.CPSChecker
            com.google.errorprone.sample.EffectivelyFinalChecker
            """);
    var customCheckSourceDir =
        Files.createDirectories(
            customCheckProjectDir.resolve("src/main/java/com/google/errorprone/sample"));
    try (var customCheckSource =
        requireNonNull(
            getClass().getResourceAsStream("/com/google/errorprone/sample/CPSChecker.java"))) {
      Files.copy(customCheckSource, customCheckSourceDir.resolve("CPSChecker.java"));
    }
    try (var customCheckSource =
        requireNonNull(
            getClass()
                .getResourceAsStream(
                    "/com/google/errorprone/sample/EffectivelyFinalChecker.java"))) {
      Files.copy(customCheckSource, customCheckSourceDir.resolve("EffectivelyFinalChecker.java"));
    }

    Files.writeString(
        getBuildFile(),
        // language=kts
        """

        dependencies {
            errorprone(project(":customCheck"))
        }
        tasks.withType<JavaCompile>().configureEach {
            options.errorprone.error("CPSChecker", "EffectivelyFinalChecker")
        }
        """,
        StandardOpenOption.APPEND);
    var sourceDir =
        Files.createDirectories(projectDir.resolve("src/main/java/com/google/errorprone/sample"));
    Files.writeString(
        sourceDir.resolve("A.java"),
        // language=java
        """
        package com.google.errorprone.sample;

        class A {
          int f(int x) {
            return x;
          }
        }
        """);
    Files.writeString(
        sourceDir.resolve("B.java"),
        // language=java
        """
        package com.google.errorprone.sample;

        class B {
          int f(int x) {
            return x;
          }
        }
        """);

    // when
    var result = buildWithArgsAndFail("compileJava");

    // then
    assertThat(requireNonNull(result.task(":compileJava")).getOutcome())
        .isEqualTo(TaskOutcome.FAILED);
    assertThat(result.getOutput()).contains("A.java:5: error: [CPSChecker]");
    assertThat(result.getOutput()).contains("B.java:5: error: [CPSChecker]");
    assertThat(result.getOutput()).doesNotContain("[EffectivelyFinalChecker]");
  }
}
