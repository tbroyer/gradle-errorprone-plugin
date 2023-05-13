import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.1.0"
    id("org.jlleitschuh.gradle.ktlint") version "11.0.0"
    id("com.android.lint") version "7.3.1"
    id("org.nosphere.gradle.github.actions") version "1.3.2"
}

group = "net.ltgt.gradle"

// Make sure Gradle Module Metadata targets the appropriate JVM version
tasks.withType<JavaCompile>().configureEach {
    options.release.set(kotlinDslPluginOptions.jvmTarget.map { JavaVersion.toVersion(it).majorVersion.toInt() })
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.allWarningsAsErrors = true
}

gradle.taskGraph.whenReady {
    if (hasTask(":publishPlugins")) {
        check("git diff --quiet --exit-code".execute(null, rootDir).waitFor() == 0) { "Working tree is dirty" }
        val process = "git describe --exact-match".execute(null, rootDir)
        check(process.waitFor() == 0) { "Version is not tagged" }
        version = process.text.trim().removePrefix("v")
    }
}

val errorproneVersion = "2.19.1"

repositories {
    mavenCentral()
    google()
}

testing {
    suites {
        withType<JvmTestSuite>().configureEach {
            useJUnitJupiter("5.9.3")
            dependencies {
                implementation("com.google.truth:truth:1.1.3") {
                    // See https://github.com/google/truth/issues/333
                    exclude(group = "junit", module = "junit")
                }
                runtimeOnly("junit:junit:4.13.2") {
                    // See https://github.com/google/truth/issues/333
                    because("Truth needs it")
                }
            }
            targets.configureEach {
                testTask {
                    testLogging {
                        showExceptions = true
                        showStackTraces = true
                        exceptionFormat = TestExceptionFormat.FULL
                    }
                }
            }
        }

        val test by getting(JvmTestSuite::class) {
            dependencies {
                implementation(project())
                implementation("com.google.errorprone:error_prone_check_api:$errorproneVersion")
            }
        }
        register<JvmTestSuite>("integrationTest") {
            dependencies {
                implementation(project()) { because("Test code calls constants") }
                implementation(gradleTestKit())
                runtimeOnly(gradleKotlinDsl()) { because("Needed by ErrorPronePlugin, usually provided by Gradle at runtime") }
            }
            // associate with main Kotlin compilation to access internal constants
            kotlin.target.compilations.named(name) {
                associateWith(kotlin.target.compilations["main"])
            }
            // make plugin-under-test-metadata.properties accessible to TestKit
            gradlePlugin.testSourceSet(sources)
            targets.configureEach {
                testTask {
                    shouldRunAfter(test)

                    val testJavaToolchain = project.findProperty("test.java-toolchain")
                    testJavaToolchain?.also {
                        javaLauncher.set(
                            project.javaToolchains.launcherFor {
                                languageVersion.set(JavaLanguageVersion.of(testJavaToolchain.toString()))
                            }
                        )
                    }

                    val testGradleVersion = project.findProperty("test.gradle-version")
                    testGradleVersion?.also { systemProperty("test.gradle-version", testGradleVersion) }

                    systemProperty("errorprone.version", errorproneVersion)
                }
            }
        }
    }
}
tasks {
    check {
        dependsOn(testing.suites)
    }
}

gradlePlugin {
    website.set("https://github.com/tbroyer/gradle-errorprone-plugin")
    vcsUrl.set("https://github.com/tbroyer/gradle-errorprone-plugin")
    plugins {
        register("errorprone") {
            id = "net.ltgt.errorprone"
            displayName = "Gradle error-prone plugin"
            implementationClass = "net.ltgt.gradle.errorprone.ErrorPronePlugin"
            description = "Gradle plugin to use the error-prone compiler for Java"
            tags.addAll("javac", "error-prone")
        }
    }
}

ktlint {
    version.set("0.45.2")
    enableExperimentalRules.set(true)
    outputToConsole.set(true)
}

fun String.execute(envp: Array<String>?, workingDir: File?) =
    Runtime.getRuntime().exec(this, envp, workingDir)

val Process.text: String
    get() = inputStream.bufferedReader().readText()
