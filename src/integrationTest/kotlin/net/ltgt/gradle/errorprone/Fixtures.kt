package net.ltgt.gradle.errorprone

import com.google.common.truth.TruthJUnit.assume
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import java.io.File

val testJavaVersion = JavaVersion.toVersion(System.getProperty("test.java-version") ?: JavaVersion.current())
val testJavaHome = System.getProperty("test.java-home", System.getProperty("java.home"))
val testGradleVersion = System.getProperty("test.gradle-version")?.let(GradleVersion::version) ?: GradleVersion.current()

const val MAX_JDK8_COMPATIBLE_ERRORPRONE_VERSION = "2.10.0"

val errorproneVersion = if (testJavaVersion.isJava8) MAX_JDK8_COMPATIBLE_ERRORPRONE_VERSION else System.getProperty("errorprone.version")!!

const val FAILURE_SOURCE_COMPILATION_ERROR = "Failure.java:6: error: [ArrayEquals]"

fun File.writeSuccessSource() {
    File(this.resolve("src/main/java/test").apply { mkdirs() }, "Success.java").apply {
        createNewFile()
        writeText(
            """
            package test;

            public class Success {
                // See http://errorprone.info/bugpattern/ArrayEquals
                @SuppressWarnings("ArrayEquals")
                public boolean arrayEquals(int[] a, int[] b) {
                    return a.equals(b);
                }
            }
            """.trimIndent(),
        )
    }
}

fun File.writeFailureSource() {
    File(this.resolve("src/main/java/test").apply { mkdirs() }, "Failure.java").apply {
        createNewFile()
        writeText(
            """
            package test;

            public class Failure {
                // See http://errorprone.info/bugpattern/ArrayEquals
                public boolean arrayEquals(int[] a, int[] b) {
                    return a.equals(b);
                }
            }
            """.trimIndent(),
        )
    }
}

fun File.buildWithArgs(vararg tasks: String): BuildResult =
    prepareBuild(*tasks)
        .build()

fun File.buildWithArgsAndFail(vararg tasks: String): BuildResult =
    prepareBuild(*tasks)
        .buildAndFail()

fun File.prepareBuild(vararg tasks: String): GradleRunner =
    GradleRunner
        .create()
        .withGradleVersion(testGradleVersion.version)
        .withProjectDir(this)
        .withPluginClasspath()
        .withArguments(*tasks)
        .forwardOutput()

// Based on https://docs.gradle.org/current/userguide/compatibility.html#java_runtime
val COMPATIBLE_GRADLE_VERSIONS =
    mapOf(
        JavaVersion.VERSION_16 to GradleVersion.version("7.0"),
        JavaVersion.VERSION_17 to GradleVersion.version("7.3"),
        JavaVersion.VERSION_18 to GradleVersion.version("7.5"),
        JavaVersion.VERSION_19 to GradleVersion.version("7.6"),
        JavaVersion.VERSION_20 to GradleVersion.version("8.3"),
        JavaVersion.VERSION_21 to GradleVersion.version("8.5"),
        JavaVersion.VERSION_22 to GradleVersion.version("8.8"),
        JavaVersion.VERSION_23 to GradleVersion.version("8.10"),
    )

fun assumeCompatibleGradleAndJavaVersions() {
    assume().that(testGradleVersion >= COMPATIBLE_GRADLE_VERSIONS[testJavaVersion] ?: GradleVersion.version("6.8")).isTrue()
}
