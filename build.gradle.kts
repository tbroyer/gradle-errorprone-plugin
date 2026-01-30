import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    alias(libs.plugins.errorprone)
    alias(libs.plugins.nullaway)
    alias(libs.plugins.gradlePluginPublish)
    alias(libs.plugins.spotless)
    alias(libs.plugins.nosphereGithubActions)
}

group = "net.ltgt.gradle"

dependencies {
    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)
}

nullaway {
    onlyNullMarked = true
    jspecifyMode = true
}
tasks {
    withType<JavaCompile>().configureEach {
        options.release = 21
        options.compilerArgs.addAll(listOf("-Werror", "-Xlint:all"))
    }
    javadoc {
        (options as StandardJavadocDocletOptions).apply {
            noTimestamp()
            quiet()
            addBooleanOption("Xdoclint:-missing", true)
        }
    }
}

tasks.compileJava {
    options.release = 8
    options.compilerArgs.add("-Xlint:-options")
}
tasks.compileKotlin {
    // See https://jakewharton.com/kotlins-jdk-release-compatibility-flag/
    compilerOptions.freeCompilerArgs.add("-Xjdk-release=1.8")
    compilerOptions.jvmTarget = JvmTarget.JVM_1_8

    // For Gradle 7.1 compatibility. Gradle 7.1 embeds Kotlin 1.4, but 1.8 is the earliest we can target,
    // and there are enough tests to assert compatibility (particularly given the narrow scope of Kotlin use).
    // https://docs.gradle.org/current/userguide/compatibility.html#kotlin
    @Suppress("DEPRECATION")
    compilerOptions.apiVersion = KotlinVersion.KOTLIN_1_8
    @Suppress("DEPRECATION")
    compilerOptions.languageVersion = KotlinVersion.KOTLIN_1_8

    compilerOptions.allWarningsAsErrors = true
    // Using Kotlin 1.8 above emits a warning that would then fail the build with allWarningsAsErrors
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

testing {
    suites {
        withType<JvmTestSuite>().configureEach {
            useJUnitJupiter(libs.versions.junitJupiter)
            dependencies {
                implementation(libs.truth)
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
                implementation(libs.checkApi)
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

                    systemProperty("errorprone.version", libs.versions.get())
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
            id = "net.ltgt"
            displayName = "Gradle Error Prone plugin"
            implementationClass = "net.ltgt.gradlePlugin"
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
        ktlint(libs.versions.ktlint.get())
    }
    kotlin {
        ktlint(libs.versions.ktlint.get())
    }
    java {
        googleJavaFormat(libs.versions.googleJavaFormat.get())
    }
}

fun cmd(vararg cmdarray: String) = Runtime.getRuntime().exec(cmdarray, null, rootDir)

val Process.text: String
    get() = inputStream.bufferedReader().readText()
