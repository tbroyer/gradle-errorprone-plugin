package net.ltgt.gradle.errorprone;

import static com.google.common.truth.TruthJUnit.assume;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.gradle.api.JavaVersion;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.util.GradleVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

public abstract class BasePluginIntegrationTest {
  public static final GradleVersion testGradleVersion =
      Optional.ofNullable(System.getProperty("test.gradle-version"))
          .map(GradleVersion::version)
          .orElseGet(GradleVersion::current);

  public static final JavaVersion testJavaVersion =
      Optional.ofNullable(System.getProperty("test.java-version"))
          .map(JavaVersion::toVersion)
          .orElseGet(JavaVersion::current);
  public static final String testJavaHome =
      System.getProperty("test.java-home", System.getProperty("java.home"));

  public static final String errorproneVersion = computeJvmCompatibleErrorProneVersion();

  public static final String MAX_JDK11_COMPATIBLE_ERRORPRONE_VERSION = "2.31.0";
  public static final String MAX_JDK17_COMPATIBLE_ERRORPRONE_VERSION = "2.42.0";

  private static String computeJvmCompatibleErrorProneVersion() {
    if (testJavaVersion.isCompatibleWith(JavaVersion.VERSION_21)) {
      // This should be the latest stable version, the one the plugin has been compiled against.
      return requireNonNull(System.getProperty("errorprone.version"));
    }
    if (testJavaVersion.isCompatibleWith(JavaVersion.VERSION_17)) {
      return MAX_JDK17_COMPATIBLE_ERRORPRONE_VERSION;
    }
    return MAX_JDK11_COMPATIBLE_ERRORPRONE_VERSION;
  }

  @TempDir protected Path projectDir;

  protected Path getSettingsFile() {
    return projectDir.resolve("settings.gradle.kts");
  }

  protected Path getBuildFile() {
    return projectDir.resolve("build.gradle.kts");
  }

  @BeforeEach
  void setupProject() throws Exception {
    assumeCompatibleGradleAndJavaVersions();

    var gradleProperties = new Properties();
    gradleProperties.setProperty("org.gradle.java.home", testJavaHome);
    try (var os = Files.newOutputStream(projectDir.resolve("gradle.properties"))) {
      gradleProperties.store(os, null);
    }

    Files.createFile(getSettingsFile());
    Files.createFile(getBuildFile());
  }

  protected static final String FAILURE_SOURCE_COMPILATION_ERROR =
      "Failure.java:6: error: [ArrayEquals]";

  protected final void writeSuccessSource() throws IOException {
    Files.createDirectories(projectDir.resolve("src/main/java/test"));
    Files.writeString(
        projectDir.resolve("src/main/java/test/Success.java"),
        // language=java
        """
        package test;

        public class Success {
            // See http://errorprone.info/bugpattern/ArrayEquals
            @SuppressWarnings("ArrayEquals")
            public boolean arrayEquals(int[] a, int[] b) {
                return a.equals(b);
            }
        }
        """);
  }

  protected final void writeFailureSource() throws IOException {
    Files.createDirectories(projectDir.resolve("src/main/java/test"));
    Files.writeString(
        projectDir.resolve("src/main/java/test/Failure.java"),
        // language=java
        """
        package test;

        public class Failure {
            // See http://errorprone.info/bugpattern/ArrayEquals
            public boolean arrayEquals(int[] a, int[] b) {
                return a.equals(b);
            }
        }
        """);
  }

  protected final BuildResult buildWithArgs(String... args) throws Exception {
    return prepareBuild(args).build();
  }

  protected final BuildResult buildWithArgsAndFail(String... args) throws Exception {
    return prepareBuild(args).buildAndFail();
  }

  protected final GradleRunner prepareBuild(String... args) throws Exception {
    return GradleRunner.create()
        .withGradleVersion(testGradleVersion.getVersion())
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments(args)
        .forwardOutput();
  }

  // Based on https://docs.gradle.org/current/userguide/compatibility.html#java_runtime
  private static final Map<JavaVersion, GradleVersion> COMPATIBLE_GRADLE_VERSIONS =
      Map.of(
          JavaVersion.VERSION_17, GradleVersion.version("7.3"),
          JavaVersion.VERSION_18, GradleVersion.version("7.5"),
          JavaVersion.VERSION_19, GradleVersion.version("7.6"),
          JavaVersion.VERSION_20, GradleVersion.version("8.3"),
          JavaVersion.VERSION_21, GradleVersion.version("8.5"),
          JavaVersion.VERSION_22, GradleVersion.version("8.8"),
          JavaVersion.VERSION_23, GradleVersion.version("8.10"),
          JavaVersion.VERSION_24, GradleVersion.version("8.14"));

  private static void assumeCompatibleGradleAndJavaVersions() {
    assume()
        .that(testGradleVersion)
        .isAtLeast(
            COMPATIBLE_GRADLE_VERSIONS.getOrDefault(testJavaVersion, GradleVersion.version("7.1")));
  }
}
