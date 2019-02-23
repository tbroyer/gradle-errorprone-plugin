package net.ltgt.gradle.errorprone

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Before
import org.junit.Test
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

        private fun jvmArg(argPrefix: String) = "$JVM_ARG$argPrefix"
    }

    @Before
    fun setup() {
        buildFile.appendText("""
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
        """.trimIndent())
        writeSuccessSource()
    }

    @Test
    fun `does not configure forking in non-Java 8 VM`() {
        assume().that(testJavaHome).named("test.java-home").isNull()
        assume().that(JavaVersion.current().isJava8).named("isJava8").isFalse()

        // when
        buildWithArgs("compileJava").also { result ->
            // then
            assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.output).contains(NOT_FORKED)
            assertThat(result.output).doesNotContain(JVM_ARG)
        }

        // Test a forked task

        // given
        buildFile.appendText("""

            compileJava.options.isFork = true
        """.trimIndent())

        // when
        buildWithArgs("compileJava").also { result ->
            // then
            assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.output).contains(FORKED)
            assertThat(result.output).doesNotContain(JVM_ARG)
        }
    }

    @Test
    fun `configure forking in Java 8 VM`() {
        assume().that(testJavaHome).named("test.java-home").isNull()
        assume().that(JavaVersion.current().isJava8).named("isJava8").isTrue()

        // when
        buildWithArgs("compileJava").also { result ->
            // then
            assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.output).contains(FORKED)
            assertThat(result.output).containsMatch(JVM_ARG_BOOTCLASSPATH_ERRORPRONE_JAVAC)
        }

        // check that it doesn't mess with task avoidance

        // when
        buildWithArgs("compileJava").also { result ->
            // then
            assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        }
    }

    @Test
    fun `does not configure forking if Error Prone is disabled`() {
        assume().that(testJavaHome).named("test.java-home").isNull()
        assume().that(JavaVersion.current().isJava8).named("isJava8").isTrue()

        // given
        buildFile.appendText("""

            compileJava.options.errorprone.isEnabled = false
        """.trimIndent())

        // when
        val result = buildWithArgs("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).contains(NOT_FORKED)
        assertThat(result.output).doesNotContain(JVM_ARG)
    }

    @Test
    fun `configure bootclasspath for already-forked tasks without javaHome or executable`() {
        assume().that(testJavaHome).named("test.java-home").isNull()
        assume().that(JavaVersion.current().isJava8).named("isJava8").isTrue()

        // given
        buildFile.appendText("""

            compileJava.apply {
                options.isFork = true
                options.forkOptions.jvmArgs!!.add("-XshowSettings")
            }
        """.trimIndent())
        // when
        val result = buildWithArgs("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).contains(FORKED)
        assertThat(result.output).containsMatch(JVM_ARG_BOOTCLASSPATH_ERRORPRONE_JAVAC)
        // Check that the configured jvm arg is preserved
        assertThat(result.output).contains(jvmArg("-XshowSettings"))
    }

    @Test
    fun `does not configure bootclasspath for already-forked tasks using javaHome`() {
        assume().that(JavaVersion.current().isJava8).named("isJava8").isTrue()

        // given
        if (testJavaHome == null) {
            // forking is already configured if testJavaHome is set, so only configure here if not set
            val javaHome = System.getProperty("java.home")
            buildFile.appendText("""

                compileJava.apply {
                    options.isFork = true
                    options.forkOptions.javaHome = File(""${'"'}${javaHome.replace("\$", "\${'\$'}")}${'"'}"")
                }
            """.trimIndent())
        }
        // XXX: make it fail always, even with non-Java 8
        buildFile.appendText("""

            compileJava.doLast {
                error("Forced failure")
            }
        """.trimIndent())

        // when
        val result = buildWithArgsAndFail("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isNotNull()
        assertThat(result.output).contains(FORKED)
        assertThat(result.output).doesNotContain(JVM_ARG)
    }

    @Test
    fun `does not configure bootclasspath for already-forked tasks using executable`() {
        assume().that(testJavaHome).named("test.java-home").isNull()
        assume().that(JavaVersion.current().isJava8).named("isJava8").isTrue()

        // given
        val javaHome = System.getProperty("java.home")
        val ext = when {
            System.getProperty("os.name").startsWith("Windows") -> ".exe"
            else -> ""
        }
        buildFile.appendText("""

            compileJava.apply {
                options.isFork = true
                options.forkOptions.executable = ""${'"'}${javaHome.replace("\$", "\${'\$'}")}${File.separator}bin${File.separator}javac$ext${'"'}""
            }
        """.trimIndent())
        // XXX: make it fail always, in case our executable above is actually wrong (such as java.home pointing to a JRE, not a JDK)
        buildFile.appendText("""

            compileJava.doLast {
                error("Forced failure")
            }
        """.trimIndent())

        // when
        val result = buildWithArgsAndFail("compileJava")

        // then
        assertThat(result.task(":compileJava")?.outcome).isNotNull()
        assertThat(result.output).contains(FORKED)
        assertThat(result.output).doesNotContain(JVM_ARG_BOOTCLASSPATH)
    }

    @Test
    fun `warns if Error Prone javac dependency is not configured`() {
        assume().that(testJavaHome).named("test.java-home").isNull()
        assume().that(JavaVersion.current().isJava8).named("isJava8").isTrue()

        // given
        // Remove the errorproneJavac dependency
        buildFile.writeText(buildFile.readLines().filterNot {
            it.contains("""errorproneJavac("com.google.errorprone:javac:$errorproneJavacVersion")""")
        }.joinToString(separator = "\n"))

        // when
        buildWithArgsAndFail("compileJava").also { result ->
            // then
            assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.FAILED)
            assertThat(result.output).contains(ErrorPronePlugin.NO_JAVAC_DEPENDENCY_WARNING_MESSAGE)
            assertThat(result.output).contains(FORKED)
            assertThat(result.output).doesNotContain(JVM_ARG_BOOTCLASSPATH)
        }

        // check that adding back the dependency fixes compilation (so it was indeed caused by missing dependency) and silences the warning

        // given
        buildFile.appendText("""

            dependencies {
                errorproneJavac("com.google.errorprone:javac:$errorproneJavacVersion")
            }
        """.trimIndent())

        // when
        buildWithArgs("compileJava").also { result ->
            // then
            assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.output).doesNotContain(ErrorPronePlugin.NO_JAVAC_DEPENDENCY_WARNING_MESSAGE)
            assertThat(result.output).contains(FORKED)
            assertThat(result.output).containsMatch(JVM_ARG_BOOTCLASSPATH_ERRORPRONE_JAVAC)
        }
    }

    @Test
    fun `is build-cache friendly`() {
        assume().that(testJavaHome).named("test.java-home").isNull()
        assume().that(JavaVersion.current().isJava8).named("isJava8").isTrue()

        // given
        settingsFile.appendText("""

            buildCache {
                local(DirectoryBuildCache::class.java) {
                    directory = file("build-cache")
                }
            }
        """.trimIndent())

        // Prime the build cache

        // when
        buildWithArgs("--build-cache", "compileJava").also { result ->
            // then
            assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(result.output).contains(FORKED)
            assertThat(result.output).containsMatch(JVM_ARG_BOOTCLASSPATH_ERRORPRONE_JAVAC)
        }

        // Move the errorproneJavac dependency: first remove it, then add it back… differently
        buildFile.writeText(buildFile.readLines().filterNot {
            it.contains("""errorproneJavac("com.google.errorprone:javac:$errorproneJavacVersion")""")
        }.joinToString(separator = "\n", postfix = """

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
        """.trimIndent()))

        // when
        testProjectDir.root.resolve("build/").deleteRecursively()
        testProjectDir.root.resolve("javac/").deleteRecursively()
        buildWithArgs("--build-cache", "compileJava").also { result ->
            // then
            assertThat(result.task(":compileJava")?.outcome).isEqualTo(TaskOutcome.FROM_CACHE)
        }
    }
}
