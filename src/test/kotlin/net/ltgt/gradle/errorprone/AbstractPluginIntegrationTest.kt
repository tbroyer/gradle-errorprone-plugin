package net.ltgt.gradle.errorprone

import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

abstract class AbstractPluginIntegrationTest {

    @JvmField
    @Rule
    val testProjectDir = TemporaryFolder()

    lateinit var settingsFile: File
    lateinit var buildFile: File

    @Before
    open fun setupProject() {
        settingsFile = testProjectDir.newFile("settings.gradle.kts")
        buildFile = testProjectDir.newFile("build.gradle.kts").apply {
            writeText(
                """
                import net.ltgt.gradle.errorprone.*

                """.trimIndent()
            )
        }
    }

    protected fun writeSuccessSource() = testProjectDir.root.writeSuccessSource()

    protected fun writeFailureSource() = testProjectDir.root.writeFailureSource()

    protected fun buildWithArgs(vararg tasks: String) = testProjectDir.root.buildWithArgs(*tasks)

    protected fun buildWithArgsAndFail(vararg tasks: String) = testProjectDir.root.buildWithArgsAndFail(*tasks)
}
