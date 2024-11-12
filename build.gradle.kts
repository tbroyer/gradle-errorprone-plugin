import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.3.0"
    id("com.diffplug.spotless") version "6.25.0"
    id("com.android.lint") version "8.7.2"
    id("org.nosphere.gradle.github.actions") version "1.4.0"
}

group = "net.ltgt.gradle"

// Make sure Gradle Module Metadata targets the appropriate JVM version
tasks.withType<JavaCompile>().configureEach {
    options.release = 8
}
tasks.withType<KotlinCompile>().configureEach {
    // See https://jakewharton.com/kotlins-jdk-release-compatibility-flag/
    compilerOptions.freeCompilerArgs.add("-Xjdk-release=1.8")
    compilerOptions.jvmTarget = JvmTarget.JVM_1_8

    // For Gradle 6.8 compatibility. Gradle 6.8 embeds Kotlin 1.4.
    // https://docs.gradle.org/current/userguide/compatibility.html#kotlin
    @Suppress("DEPRECATION")
    compilerOptions.apiVersion = KotlinVersion.KOTLIN_1_4
    @Suppress("DEPRECATION")
    compilerOptions.languageVersion = KotlinVersion.KOTLIN_1_4

    compilerOptions.allWarningsAsErrors = true
    // Using Kotlin 1.4 above emits a warning that would then fail the build with allWarningsAsErrors
    compilerOptions.freeCompilerArgs.add("-Xsuppress-version-warnings")
}

gradle.taskGraph.whenReady {
    if (hasTask(":publishPlugins")) {
        check(cmd("git", "diff", "--quiet", "--exit-code").waitFor() == 0) { "Working tree is dirty" }
        val process = cmd("git", "describe", "--exact-match")
        check(process.waitFor() == 0) { "Version is not tagged" }
        version = process.text.trim().removePrefix("v")
    }
}

val errorproneVersion = "2.35.1"

repositories {
    mavenCentral()
    google()
}

testing {
    suites {
        withType<JvmTestSuite>().configureEach {
            useJUnitJupiter("5.11.3")
            dependencies {
                implementation("com.google.truth:truth:1.4.4") {
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
                        val launcher =
                            project.javaToolchains.launcherFor {
                                languageVersion.set(JavaLanguageVersion.of(testJavaToolchain.toString()))
                            }
                        val metadata = launcher.get().metadata
                        systemProperty("test.java-version", metadata.languageVersion.asInt())
                        systemProperty("test.java-home", metadata.installationPath.asFile.canonicalPath)
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
            displayName = "Gradle Error Prone plugin"
            implementationClass = "net.ltgt.gradle.errorprone.ErrorPronePlugin"
            description = "Gradle plugin to use Error Prone with the Java compiler"
            tags.addAll("javac", "error-prone")
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Gradle error-prone plugin")
            description.set("Gradle plugin to use the error-prone compiler for Java")
            url.set("https://github.com/tbroyer/gradle-errorprone-plugin")
            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                }
            }
            developers {
                developer {
                    name.set("Thomas Broyer")
                    email.set("t.broyer@ltgt.net")
                }
            }
            scm {
                connection.set("https://github.com/tbroyer/gradle-errorprone-plugin.git")
                developerConnection.set("scm:git:ssh://github.com:tbroyer/gradle-errorprone-plugin.git")
                url.set("https://github.com/tbroyer/gradle-errorprone-plugin")
            }
        }
    }
}

spotless {
    kotlinGradle {
        ktlint("1.4.1")
    }
    kotlin {
        ktlint("1.4.1")
    }
}

fun cmd(vararg cmdarray: String) = Runtime.getRuntime().exec(cmdarray, null, rootDir)

val Process.text: String
    get() = inputStream.bufferedReader().readText()
