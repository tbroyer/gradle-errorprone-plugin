package net.ltgt.gradle.errorprone

import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import java.io.File

val testGradleVersion = System.getProperty("test.gradle-version", GradleVersion.current().version)

const val MAX_JDK8_COMPATIBLE_ERRORPRONE_VERSION = "2.10.0"

val errorproneVersion = if (JavaVersion.current().isJava8) MAX_JDK8_COMPATIBLE_ERRORPRONE_VERSION else System.getProperty("errorprone.version")!!

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
            """.trimIndent()
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
            """.trimIndent()
        )
    }
}

fun File.buildWithArgs(vararg tasks: String): BuildResult {
    return prepareBuild(*tasks)
        .build()
}

fun File.buildWithArgsAndFail(vararg tasks: String): BuildResult {
    return prepareBuild(*tasks)
        .buildAndFail()
}

fun File.prepareBuild(vararg tasks: String): GradleRunner {
    return GradleRunner.create()
        .withGradleVersion(testGradleVersion)
        .withProjectDir(this)
        .withPluginClasspath()
        .withArguments(*tasks)
        .forwardOutput()
}
