import com.android.Version
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "0.21.0"
    id("org.jlleitschuh.gradle.ktlint") version "11.0.0"
    id("com.android.lint") version "4.2.2"
    id("org.nosphere.gradle.github.actions") version "1.3.2"
}
buildscript {
    dependencyLocking {
        lockAllConfigurations()
        lockMode.set(LockMode.STRICT)
    }
}
dependencyLocking {
    lockAllConfigurations()
    lockMode.set(LockMode.STRICT)
}

group = "net.ltgt.gradle"

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

// See https://github.com/gradle/gradle/issues/7974
val additionalPluginClasspath by configurations.creating

val errorproneVersion = "2.10.0"

repositories {
    mavenCentral()
    google()
}
dependencies {
    compileOnly("com.android.tools.build:gradle:${Version.ANDROID_GRADLE_PLUGIN_VERSION}")
    additionalPluginClasspath("com.android.tools.build:gradle:${Version.ANDROID_GRADLE_PLUGIN_VERSION}")

    testImplementation("com.google.truth:truth:1.1.3") {
        // See https://github.com/google/truth/issues/333
        exclude(group = "junit", module = "junit")
    }
    testRuntimeOnly("junit:junit:4.13.2") {
        // See https://github.com/google/truth/issues/333
        because("Truth needs it")
    }
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.1")

    testImplementation("com.google.errorprone:error_prone_check_api:$errorproneVersion")
}

tasks {
    pluginUnderTestMetadata {
        this.pluginClasspath.from(additionalPluginClasspath)
    }

    test {
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

        val androidSdkHome = project.findProperty("test.android-sdk-home")
            ?: System.getenv("ANDROID_SDK_ROOT") ?: System.getenv("ANDROID_HOME")
        androidSdkHome?.also { systemProperty("test.android-sdk-home", androidSdkHome) }

        systemProperty("errorprone.version", errorproneVersion)

        if (project.findProperty("test.skipAndroid").toString().toBoolean()) {
            exclude("**/*Android*")
        }

        useJUnitPlatform()
        testLogging {
            showExceptions = true
            showStackTraces = true
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}

gradlePlugin {
    plugins {
        register("errorprone") {
            id = "net.ltgt.errorprone"
            displayName = "Gradle error-prone plugin"
            implementationClass = "net.ltgt.gradle.errorprone.ErrorPronePlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/tbroyer/gradle-errorprone-plugin"
    vcsUrl = "https://github.com/tbroyer/gradle-errorprone-plugin"
    description = "Gradle plugin to use the error-prone compiler for Java"
    tags = listOf("javac", "error-prone")

    mavenCoordinates {
        groupId = project.group.toString()
        artifactId = project.name
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
