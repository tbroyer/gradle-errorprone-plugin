package net.ltgt.gradle.errorprone.javacplugin

import com.google.common.truth.Truth.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Before
import org.junit.Test
import java.io.File

class AndroidIntegrationTest : AbstractPluginIntegrationTest() {
    private val androidSdkHome = System.getProperty("test.android-sdk-home")
    private val androidPluginVersion = System.getProperty("android-plugin.version")!!

    @Before
    fun setupAndroid() {
        assertThat(androidSdkHome).isNotEmpty()

        testProjectDir.newFile("local.properties").writeText("sdk.dir=$androidSdkHome")

        settingsFile.appendText("""
            pluginManagement {
                repositories {
                    google()
                    jcenter()
                }
                resolutionStrategy {
                    eachPlugin {
                        if (requested.id.namespace == "com.android") {
                            useModule("com.android.tools.build:gradle:${'$'}{requested.version}")
                        }
                    }
                }
            }
        """.trimIndent())

        buildFile.appendText("""
            plugins {
                id("${ErrorProneJavacPluginPlugin.PLUGIN_ID}")
                id("com.android.application") version "$androidPluginVersion"
            }

            android {
                compileSdkVersion(26)
                defaultConfig {
                    minSdkVersion(15)
                    targetSdkVersion(26)
                    versionCode = 1
                    versionName = "1.0"
                }
            }

            repositories {
                jcenter()
            }
            dependencies {
                errorprone("com.google.errorprone:error_prone_core:$errorproneVersion")
            }
        """.trimIndent())

        File(testProjectDir.newFolder("src", "main"), "AndroidManifest.xml").writeText("""
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example">
            </manifest>
        """.trimIndent())
    }

    @Test
    fun `compilation succeeds`() {
        // given
        writeSuccessSource()

        // when
        val result = buildWithArgs("compileReleaseJavaWithJavac")

        // then
        assertThat(result.task(":compileReleaseJavaWithJavac")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `compilation fails`() {
        // given
        writeFailureSource()

        // when
        val result = buildWithArgsAndFail("compileReleaseJavaWithJavac")

        // then
        assertThat(result.task(":compileReleaseJavaWithJavac")?.outcome).isEqualTo(TaskOutcome.FAILED)
        assertThat(result.output).contains("Failure.java:6: error: [ArrayEquals]")
    }

    @Test
    fun `can configure errorprone`() {
        // given
        buildFile.appendText("""

            afterEvaluate {
                tasks.withType<JavaCompile>() {
                    options.errorprone {
                        check("ArrayEquals", CheckSeverity.OFF)
                    }
                }
            }
        """.trimIndent())
        writeFailureSource()

        // when
        val result = buildWithArgs("compileReleaseJavaWithJavac")

        // then
        assertThat(result.task(":compileReleaseJavaWithJavac")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `can disable errorprone`() {
        // given
        buildFile.appendText("""

            afterEvaluate {
                tasks.withType<JavaCompile>() {
                    options.errorprone.isEnabled = false
                }
            }
        """.trimIndent())
        writeFailureSource()

        // when
        val result = buildWithArgs("compileReleaseJavaWithJavac")

        // then
        assertThat(result.task(":compileReleaseJavaWithJavac")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }
}
