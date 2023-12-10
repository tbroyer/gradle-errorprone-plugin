package net.ltgt.gradle.errorprone

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Properties

abstract class AbstractPluginIntegrationTest {

    @TempDir
    lateinit var testProjectDir: File
    lateinit var settingsFile: File
    lateinit var buildFile: File

    @BeforeEach
    open fun setupProject() {
        testProjectDir.resolve("gradle.properties").outputStream().use {
            Properties().apply {
                setProperty("org.gradle.java.home", testJavaHome)
                store(it, null)
            }
        }
        settingsFile = testProjectDir.resolve("settings.gradle.kts").apply {
            createNewFile()
        }
        buildFile = testProjectDir.resolve("build.gradle.kts").apply {
            writeText(
                """
                import net.ltgt.gradle.errorprone.*

                """.trimIndent(),
            )
        }
    }
}
