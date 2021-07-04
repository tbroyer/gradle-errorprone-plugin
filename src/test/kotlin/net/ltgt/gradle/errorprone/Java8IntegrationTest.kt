package net.ltgt.gradle.errorprone

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class Java8IntegrationTest : AbstractPluginIntegrationTest() {

    companion object {
        private val FORKED = "${System.lineSeparator()}Fork: true${System.lineSeparator()}"
        private val NOT_FORKED = "${System.lineSeparator()}Fork: false${System.lineSeparator()}"
        private val JVM_ARG = "${System.lineSeparator()}JVM Arg: "
        private val JVM_ARG_BOOTCLASSPATH = jvmArg("-Xbootclasspath/p:")
        private val JVM_ARG_BOOTCLASSPATH_ERRORPRONE_JAVAC = Regex.escape(File.separator).let { fileSeparator ->
            val escapedErrorproneJavacVersion = Regex.escape(errorproneJavacVersion)
            """$JVM_ARG_BOOTCLASSPATH.*${fileSeparator}com\.google\.errorprone${fileSeparator}javac$fileSeparator$escapedErrorproneJavacVersion$fileSeparator.*${fileSeparator}javac-$escapedErrorproneJavacVersion.jar[${File.pathSeparator}${System.lineSeparator()}]"""
                .toPattern()
        }
        private val JVM_ARGS_STRONG_ENCAPSULATION = ErrorPronePlugin.JVM_ARGS_STRONG_ENCAPSULATION.joinToString(prefix = JVM_ARG, separator = JVM_ARG)

        private fun jvmArg(argPrefix: String) = "$JVM_ARG$argPrefix"
    }

    @BeforeEach
    fun setup() {
        buildFile.appendText(
            """
            plugins {
                `java-library`
                id("${ErrorPronePlugin.PLUGIN_ID}")
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                errorprone("com.google.errorprone:error_prone_core:$errorproneVersion")
                errorproneJavac("com.google.errorprone:javac:$errorproneJavacVersion")
            }

            val compileJava: JavaCompile by tasks
            val displayCompileJavaOptions by tasks.creating {
                doFirst {
                    println("Fork: ${'$'}{compileJava.options.isFork}")
                    compileJava.options.forkOptions.jvmArgs?.forEach { arg ->
                        println("JVM Arg: ${'$'}arg")
                    }
                }
            }
            compileJava.finalizedBy(displayCompileJavaOptions)
            compileJava.options.forkOptions.jvmArgs!!.add("-XshowSettings")
            """.trimIndent()
        )
        if (JavaVersion.current().isJava16Compatible && GradleVersion.version(testGradleVersion) < GradleVersion.version("7.0")) {
            // https://melix.github.io/blog/2021/03/gradle-java16.html
            buildFile.appendText(
                """

                tasks.withType<JavaCompile>().configureEach {
                    options.isIncremental = false
                }
                """.trimIndent()
            )
        }
        testProjectDir.writeSuccessSource()
    }

    @Test
    fun `does not configure forking in non-Java 8 or 16+ VM`() {
        assume().withMessage("isJava8").that(JavaVersion.current().isJava8).isFalse()
        assume().withMessage("isJava16Compatible").that(JavaVersion.current()).isLessThan(JavaVersion.VERSION_16)

        // when
        testProjectDir.buildWithArgs("compileJava").also { result ->
            // then
            assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.output).contains(NOT_FORKED)
            // Check that the configured jvm arg is preserved
            assertThat(result.output).contains(jvmArg("-XshowSettings"))
        }

        // Test a forked task

        // given
        buildFile.appendText(
            """

            compileJava.options.isFork = true
            """.trimIndent()
        )

        // when
        testProjectDir.buildWithArgs("compileJava").also { result ->
            // then
            assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.output).contains(FORKED)
            assertThat(result.output).doesNotContain(JVM_ARG_BOOTCLASSPATH)
            // Check that the configured jvm arg is preserved
            assertThat(result.output).contains(jvmArg("-XshowSettings"))
        }
    }

    @Test
    fun `configure forking in Java 8 VM`() {
        assume().withMessage("isJava8").that(JavaVersion.current().isJava8).isTrue()

        // when
        testProjectDir.buildWithArgs("compileJava").also { result ->
            // then
            assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.output).contains(FORKED)
            assertThat(result.output).containsMatch(JVM_ARG_BOOTCLASSPATH_ERRORPRONE_JAVAC)
        }

        // check that it doesn't mess with task avoidance

        // when
        testProjectDir.buildWithArgs("compileJava").also { result ->
            // then
            assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        }
    }

    @Test
    fun `configure forking in Java 16+ VM`() {
        assume().withMessage("isJava16Compatible").that(JavaVersion.current()).isAtLeast(JavaVersion.VERSION_16)

        // when
        testProjectDir.buildWithArgs("compileJava").also { result ->
            // then
            assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.output).contains(FORKED)
            assertThat(result.output).contains(JVM_ARGS_STRONG_ENCAPSULATION)
            // Check that the configured jvm arg is removed
            assertThat(result.output).doesNotContain(jvmArg("-XshowSettings"))
        }

        // check that it doesn't mess with task avoidance

        // when
        testProjectDir.buildWithArgs("compileJava").also { result ->
            // then
            assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        }
    }

    @Test
    fun `does not configure forking if Error Prone is disabled`() {
        assume().withMessage("isJava8Or16plus").that(JavaVersion.current().run { isJava8 || isJava16Compatible }).isTrue()

        // given
        buildFile.appendText(
            """

            compileJava.options.errorprone.isEnabled.set(false)
            """.trimIndent()
        )

        // when
        val result = testProjectDir.buildWithArgs("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).contains(NOT_FORKED)
        assertThat(result.output).doesNotContain(JVM_ARG_BOOTCLASSPATH)
        // Check that the configured jvm arg is preserved
        assertThat(result.output).contains(jvmArg("-XshowSettings"))
    }

    @Test
    fun `configure bootclasspath for already-forked tasks without javaHome or executable`() {
        assume().withMessage("isJava8").that(JavaVersion.current().isJava8).isTrue()

        // given
        buildFile.appendText(
            """

            compileJava.apply {
                options.isFork = true
            }
            """.trimIndent()
        )
        // when
        val result = testProjectDir.buildWithArgs("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).contains(FORKED)
        assertThat(result.output).containsMatch(JVM_ARG_BOOTCLASSPATH_ERRORPRONE_JAVAC)
        // Check that the configured jvm arg is preserved
        assertThat(result.output).contains(jvmArg("-XshowSettings"))
    }

    @Test
    fun `does not configure bootclasspath for already-forked tasks using javaHome`() {
        assume().withMessage("isJava8").that(JavaVersion.current().isJava8).isTrue()

        // given
        val javaHome = System.getProperty("java.home")
        buildFile.appendText(
            """

            compileJava.apply {
                options.isFork = true
                options.forkOptions.javaHome = File(""${'"'}${javaHome.replace("\$", "\${'\$'}")}${'"'}"")
            }
            """.trimIndent()
        )
        // XXX: make it fail always, even with non-Java 8
        buildFile.appendText(
            """

            compileJava.doLast {
                error("Forced failure")
            }
            """.trimIndent()
        )

        // when
        val result = testProjectDir.buildWithArgsAndFail("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isNotNull()
        assertThat(result.output).contains(FORKED)
        assertThat(result.output).doesNotContain(JVM_ARG_BOOTCLASSPATH)
        // Check that the configured jvm arg is preserved
        assertThat(result.output).contains(jvmArg("-XshowSettings"))
    }

    @Test
    fun `does not configure bootclasspath for already-forked tasks using executable`() {
        assume().withMessage("isJava8").that(JavaVersion.current().isJava8).isTrue()

        // given
        val javaHome = System.getProperty("java.home")
        val ext = when {
            System.getProperty("os.name").startsWith("Windows") -> ".exe"
            else -> ""
        }
        buildFile.appendText(
            """

            compileJava.apply {
                options.isFork = true
                options.forkOptions.executable = ""${'"'}${javaHome.replace("\$", "\${'\$'}")}${File.separator}bin${File.separator}javac$ext${'"'}""
            }
            """.trimIndent()
        )
        // XXX: make it fail always, in case our executable above is actually wrong (such as java.home pointing to a JRE, not a JDK)
        buildFile.appendText(
            """

            compileJava.doLast {
                error("Forced failure")
            }
            """.trimIndent()
        )

        // when
        val result = testProjectDir.buildWithArgsAndFail("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isNotNull()
        assertThat(result.output).contains(FORKED)
        assertThat(result.output).doesNotContain(JVM_ARG_BOOTCLASSPATH)
        // Check that the configured jvm arg is preserved
        assertThat(result.output).contains(jvmArg("-XshowSettings"))
    }

    @Test
    fun `warns if Error Prone javac dependency is not configured`() {
        assume().withMessage("isJava8").that(JavaVersion.current().isJava8).isTrue()

        // given
        // Remove the errorproneJavac dependency
        buildFile.writeText(
            buildFile.readLines().filterNot {
                it.contains("""errorproneJavac("com.google.errorprone:javac:$errorproneJavacVersion")""")
            }.joinToString(separator = "\n")
        )

        // when
        testProjectDir.buildWithArgsAndFail("compileJava").also { result ->
            // then
            assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.FAILED)
            assertThat(result.output).contains(ErrorPronePlugin.NO_JAVAC_DEPENDENCY_WARNING_MESSAGE)
            assertThat(result.output).contains(FORKED)
            assertThat(result.output).doesNotContain(JVM_ARG_BOOTCLASSPATH)
            // Check that the configured jvm arg is removed
            assertThat(result.output).doesNotContain(jvmArg("-XshowSettings"))
        }

        // check that adding back the dependency fixes compilation (so it was indeed caused by missing dependency) and silences the warning

        // given
        buildFile.appendText(
            """

            dependencies {
                errorproneJavac("com.google.errorprone:javac:$errorproneJavacVersion")
            }
            """.trimIndent()
        )

        // when
        testProjectDir.buildWithArgs("compileJava").also { result ->
            // then
            assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.output).doesNotContain(ErrorPronePlugin.NO_JAVAC_DEPENDENCY_WARNING_MESSAGE)
            assertThat(result.output).contains(FORKED)
            assertThat(result.output).containsMatch(JVM_ARG_BOOTCLASSPATH_ERRORPRONE_JAVAC)
            // Check that the configured jvm arg is removed
            assertThat(result.output).doesNotContain(jvmArg("-XshowSettings"))
        }
    }

    @Test
    fun `does not warn if Error Prone javac dependency is not configured with non-Java 8 VM`() {
        assume().withMessage("isJava8").that(JavaVersion.current().isJava8).isFalse()

        // given
        // Remove the errorproneJavac dependency
        buildFile.writeText(
            buildFile.readLines().filterNot {
                it.contains("""errorproneJavac("com.google.errorprone:javac:$errorproneJavacVersion")""")
            }.joinToString(separator = "\n")
        )

        // when
        testProjectDir.buildWithArgs("compileJava").also { result ->
            // then
            assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.output).doesNotContain(ErrorPronePlugin.NO_JAVAC_DEPENDENCY_WARNING_MESSAGE)
            assertThat(result.output).doesNotContain(JVM_ARG_BOOTCLASSPATH)
        }
    }

    @Test
    fun `is build-cache friendly`() {
        assume().withMessage("isJava8").that(JavaVersion.current().isJava8).isTrue()

        // given
        settingsFile.appendText(
            """

            buildCache {
                local${
            if (GradleVersion.version(testGradleVersion) < GradleVersion.version("6.0")) {
                "(DirectoryBuildCache::class.java)"
            } else {
                ""
            }
            } {
                    directory = file("build-cache")
                }
            }
            """.trimIndent()
        )

        // Prime the build cache

        // when
        testProjectDir.buildWithArgs("--build-cache", "compileJava").also { result ->
            // then
            assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.output).contains(FORKED)
            assertThat(result.output).containsMatch(JVM_ARG_BOOTCLASSPATH_ERRORPRONE_JAVAC)
            // Check that the configured jvm arg is removed
            assertThat(result.output).doesNotContain(jvmArg("-XshowSettings"))
        }

        // Move the errorproneJavac dependency: first remove it, then add it back… differently
        buildFile.writeText(
            buildFile.readLines().filterNot {
                it.contains("""errorproneJavac("com.google.errorprone:javac:$errorproneJavacVersion")""")
            }.joinToString(
                separator = "\n",
                postfix = """

                    val epJavac by configurations.creating
                    val moveEpJavac by tasks.creating(Copy::class) {
                        from(epJavac)
                        // destinationDir chosen to match JVM_ARG_BOOTCLASSPATH_ERRORPRONE_JAVAC
                        into(file("javac/com.google.errorprone/javac/$errorproneJavacVersion/foo/"))
                        rename { "renamed-${'$'}it" }
                    }
                    dependencies {
                        epJavac("com.google.errorprone:javac:$errorproneJavacVersion")
                        errorproneJavac(fileTree(moveEpJavac.destinationDir).builtBy(moveEpJavac))
                    }
                """.trimIndent()
            )
        )

        // when
        testProjectDir.resolve("build/").deleteRecursively()
        testProjectDir.resolve("javac/").deleteRecursively()
        testProjectDir.buildWithArgs("--build-cache", "compileJava").also { result ->
            // then
            assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.FROM_CACHE)
        }
    }
}
