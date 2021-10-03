package net.ltgt.gradle.errorprone

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties

class AndroidIntegrationTest : AbstractPluginIntegrationTest() {
    private val androidSdkHome = System.getProperty("test.android-sdk-home")

    @BeforeEach
    fun setupAndroid() {
        assume().withMessage("isJava16Compatible").that(JavaVersion.current()).isLessThan(JavaVersion.VERSION_16)
        assertThat(androidSdkHome).isNotEmpty()

        Properties().apply {
            set("sdk.dir", androidSdkHome)
            testProjectDir.resolve("local.properties").outputStream().use {
                store(it, null)
            }
        }

        buildFile.appendText(
            """
            plugins {
                id("${ErrorPronePlugin.PLUGIN_ID}")
                id("com.android.application")
            }

            android {
                compileSdkVersion(30)
                defaultConfig {
                    minSdkVersion(15)
                    targetSdkVersion(30)
                    versionCode = 1
                    versionName = "1.0"
                }
            }

            repositories {
                mavenCentral()
                google()
            }
            dependencies {
                errorprone("com.google.errorprone:error_prone_core:$errorproneVersion")
            }
            """.trimIndent()
        )

        File(testProjectDir.resolve("src/main").apply { mkdirs() }, "AndroidManifest.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example">
            </manifest>
            """.trimIndent()
        )
    }

    @Test
    fun `compilation succeeds`() {
        // given
        testProjectDir.writeSuccessSource()

        // when
        val result = testProjectDir.buildWithArgs("compileReleaseJavaWithJavac")

        // then
        assertThat(result.task(":compileReleaseJavaWithJavac")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `compilation fails`() {
        // given
        testProjectDir.writeFailureSource()

        // when
        val result = testProjectDir.buildWithArgsAndFail("compileReleaseJavaWithJavac")

        // then
        assertThat(result.task(":compileReleaseJavaWithJavac")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains(FAILURE_SOURCE_COMPILATION_ERROR)
    }

    @Test
    fun `can configure errorprone`() {
        // given
        buildFile.appendText(
            """

            tasks.withType<JavaCompile>().configureEach {
                options.errorprone {
                    disable("ArrayEquals")
                }
            }
            """.trimIndent()
        )
        testProjectDir.writeFailureSource()

        // when
        val result = testProjectDir.buildWithArgs("compileReleaseJavaWithJavac")

        // then
        assertThat(result.task(":compileReleaseJavaWithJavac")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `can disable errorprone`() {
        // given
        buildFile.appendText(
            """

            tasks.withType<JavaCompile>().configureEach {
                options.errorprone.isEnabled.set(false)
            }
            """.trimIndent()
        )
        testProjectDir.writeFailureSource()

        // when
        val result = testProjectDir.buildWithArgs("compileReleaseJavaWithJavac")

        // then
        assertThat(result.task(":compileReleaseJavaWithJavac")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }
}
